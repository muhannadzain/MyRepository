//! Core logic: local storage + sandboxed script runner. No UI dependencies (testable on any host).
use rhai::{Engine, Scope, AST};
use serde::{Deserialize, Serialize};
use std::cell::RefCell;
use std::collections::{BTreeMap, HashMap, HashSet};
use std::path::PathBuf;
use std::rc::Rc;

pub const MAX_CAPTION: usize = 500;
pub const MAX_CODE: usize = 20_000;
const MAX_KEYS: usize = 50;
const MAX_OPTIONS: usize = 20;
const MAX_KEY_LEN: usize = 64;
const MAX_CMDS: usize = 200;
const MAX_WRITES: usize = 5;

/// Poll template: one vote per user (changeable), shared counts.
pub const TPL_POLL: &str = r#"let opts = ["Option A", "Option B", "Option C"];
let total = 0;
for o in opts { total += ui.votes(o); }
ui.label("Which one do you pick?");
for o in opts {
    let n = ui.votes(o);
    let frac = if total == 0 { 0.0 } else { n.to_float() / total.to_float() };
    let mark = if ui.my_vote() == o { "  (your vote)" } else { "" };
    ui.bar(frac, "" + o + ": " + n + mark);
    if ui.button("Vote " + o) { ui.vote(o); }
}
"#;

/// Interactive stat-bar template: shared counter with a goal.
pub const TPL_METER: &str = r#"let goal = 20;
let n = ui.count("clicks");
ui.label("Community counter");
ui.bar(n.to_float() / goal.to_float(), "" + n + " / " + goal);
if ui.button("+1") { ui.incr("clicks"); }
"#;

// ------------------------------------------------------------------ storage

#[derive(Serialize, Deserialize, Clone, Default, Debug)]
pub struct Post {
    pub id: u64,
    pub author: String,
    pub caption: String,
    pub code: String,
    #[serde(default)]
    pub counters: BTreeMap<String, i64>,
    /// user -> chosen option (one vote per user)
    #[serde(default)]
    pub votes: BTreeMap<String, String>,
}

#[derive(Serialize, Deserialize, Default)]
struct Db {
    next_id: u64,
    posts: Vec<Post>,
}

pub struct Store {
    db: Db,
    path: Option<PathBuf>,
}

fn valid_key(k: &str) -> bool {
    !k.is_empty() && k.chars().count() <= MAX_KEY_LEN
}

impl Store {
    pub fn load(dir: Option<PathBuf>) -> Self {
        let path = dir.map(|d| d.join("data.json"));
        let loaded = path
            .as_ref()
            .and_then(|p| std::fs::read_to_string(p).ok())
            .and_then(|s| serde_json::from_str::<Db>(&s).ok());
        match loaded {
            Some(db) => Store { db, path },
            None => {
                let mut s = Store { db: Db { next_id: 1, posts: vec![] }, path };
                s.seed();
                s
            }
        }
    }

    fn seed(&mut self) {
        let _ = self.add_post("demo", "Interactive counter (shared bar). Tap +1!", TPL_METER);
        let _ = self.add_post("demo", "Poll made with a code block. Switch accounts at the top to vote as someone else.", TPL_POLL);
        let _ = self.add_post("demo", "Welcome to CodePost: a post can carry a live, sandboxed code block. Everything here works offline.", "");
    }

    pub fn posts(&self) -> &[Post] {
        &self.db.posts
    }

    fn save(&self) {
        if let Some(p) = &self.path {
            if let Ok(s) = serde_json::to_string(&self.db) {
                let tmp = p.with_extension("json.tmp");
                if std::fs::write(&tmp, s).is_ok() {
                    let _ = std::fs::rename(&tmp, p);
                }
            }
        }
    }

    pub fn add_post(&mut self, author: &str, caption: &str, code: &str) -> Result<(), String> {
        let caption = caption.trim();
        if caption.is_empty() && code.trim().is_empty() {
            return Err("Empty post".into());
        }
        if caption.chars().count() > MAX_CAPTION {
            return Err(format!("Caption too long (max {MAX_CAPTION})"));
        }
        if code.len() > MAX_CODE {
            return Err(format!("Code too long (max {MAX_CODE} bytes)"));
        }
        let id = self.db.next_id.max(1);
        self.db.next_id = id + 1;
        self.db.posts.insert(
            0,
            Post { id, author: author.to_string(), caption: caption.to_string(), code: code.to_string(), ..Default::default() },
        );
        self.save();
        Ok(())
    }

    pub fn delete_post(&mut self, id: u64, user: &str) -> bool {
        let before = self.db.posts.len();
        self.db.posts.retain(|p| !(p.id == id && p.author == user));
        let changed = self.db.posts.len() != before;
        if changed {
            self.save();
        }
        changed
    }

    /// Shared atomic counter (+1).
    pub fn incr(&mut self, post_id: u64, key: &str) -> bool {
        if !valid_key(key) {
            return false;
        }
        let Some(p) = self.db.posts.iter_mut().find(|p| p.id == post_id) else { return false };
        if !p.counters.contains_key(key) && p.counters.len() >= MAX_KEYS {
            return false;
        }
        let e = p.counters.entry(key.to_string()).or_insert(0);
        *e = e.saturating_add(1);
        self.save();
        true
    }

    /// One vote per user; changing it moves the vote. Returns true if anything changed.
    pub fn vote(&mut self, post_id: u64, user: &str, option: &str) -> bool {
        if !valid_key(option) {
            return false;
        }
        let Some(p) = self.db.posts.iter_mut().find(|p| p.id == post_id) else { return false };
        if p.votes.get(user).map(|s| s.as_str()) == Some(option) {
            return false;
        }
        let too_many = {
            let existing: HashSet<&str> = p.votes.values().map(|s| s.as_str()).collect();
            !existing.contains(option) && existing.len() >= MAX_OPTIONS
        };
        if too_many {
            return false;
        }
        p.votes.insert(user.to_string(), option.to_string());
        self.save();
        true
    }
}

// ------------------------------------------------------------------ script runner

/// What a script can see about its post (a read-only copy).
#[derive(Clone, Default)]
pub struct Snapshot {
    pub me: String,
    pub counters: BTreeMap<String, i64>,
    pub vote_counts: BTreeMap<String, i64>,
    pub my_vote: String,
}

impl Snapshot {
    pub fn empty(me: &str) -> Self {
        Snapshot { me: me.to_string(), ..Default::default() }
    }
    pub fn from_post(p: &Post, me: &str) -> Self {
        let mut vote_counts: BTreeMap<String, i64> = BTreeMap::new();
        for o in p.votes.values() {
            *vote_counts.entry(o.clone()).or_insert(0) += 1;
        }
        Snapshot {
            me: me.to_string(),
            counters: p.counters.clone(),
            vote_counts,
            my_vote: p.votes.get(me).cloned().unwrap_or_default(),
        }
    }
}

/// Drawing commands a script can emit.
#[derive(Clone, Debug, PartialEq)]
pub enum Cmd {
    Label(String),
    Button(usize, String),
    Bar(f32, String),
}

#[derive(Default, Debug)]
pub struct Output {
    pub cmds: Vec<Cmd>,
    pub incr: Vec<String>,
    pub vote: Option<String>,
    pub error: Option<String>,
}

struct HostInner {
    snap: Snapshot,
    clicked: HashSet<usize>,
    next_btn: usize,
    cmds: Vec<Cmd>,
    incr: Vec<String>,
    vote: Option<String>,
}

#[derive(Clone)]
struct Host(Rc<RefCell<HostInner>>);

fn clip(s: String) -> String {
    if s.chars().count() > 300 {
        s.chars().take(300).collect()
    } else {
        s
    }
}

fn clamp01(f: f64) -> f32 {
    if f.is_finite() {
        f.clamp(0.0, 1.0) as f32
    } else {
        0.0
    }
}

fn push(h: &Host, c: Cmd) {
    let mut i = h.0.borrow_mut();
    if i.cmds.len() < MAX_CMDS {
        i.cmds.push(c);
    }
}

fn register(engine: &mut Engine) {
    engine.register_type_with_name::<Host>("Ui");
    engine.register_fn("label", |h: &mut Host, t: String| push(h, Cmd::Label(clip(t))));
    engine.register_fn("button", |h: &mut Host, t: String| -> bool {
        let mut i = h.0.borrow_mut();
        let id = i.next_btn;
        i.next_btn += 1;
        if i.cmds.len() < MAX_CMDS {
            i.cmds.push(Cmd::Button(id, clip(t)));
        }
        i.clicked.contains(&id)
    });
    engine.register_fn("bar", |h: &mut Host, f: f64, t: String| push(h, Cmd::Bar(clamp01(f), clip(t))));
    engine.register_fn("bar", |h: &mut Host, f: i64, t: String| push(h, Cmd::Bar(clamp01(f as f64), clip(t))));
    engine.register_fn("count", |h: &mut Host, k: String| -> i64 {
        h.0.borrow().snap.counters.get(&k).copied().unwrap_or(0)
    });
    engine.register_fn("incr", |h: &mut Host, k: String| {
        let mut i = h.0.borrow_mut();
        if i.incr.len() < MAX_WRITES && valid_key(&k) {
            i.incr.push(k);
        }
    });
    engine.register_fn("votes", |h: &mut Host, o: String| -> i64 {
        h.0.borrow().snap.vote_counts.get(&o).copied().unwrap_or(0)
    });
    engine.register_fn("vote", |h: &mut Host, o: String| {
        let mut i = h.0.borrow_mut();
        if valid_key(&o) {
            i.vote = Some(o);
        }
    });
    engine.register_fn("my_vote", |h: &mut Host| -> String { h.0.borrow().snap.my_vote.clone() });
    engine.register_fn("me", |h: &mut Host| -> String { h.0.borrow().snap.me.clone() });
}

pub struct Runner {
    engine: Engine,
    asts: HashMap<u64, (String, Result<AST, String>)>,
}

impl Default for Runner {
    fn default() -> Self {
        Self::new()
    }
}

impl Runner {
    pub fn new() -> Self {
        let mut engine = Engine::new();
        // Hard limits: an endless loop or a memory bomb in a post can't freeze or crash the app.
        engine.set_max_operations(20_000);
        engine.set_max_call_levels(16);
        engine.set_max_expr_depths(64, 32);
        engine.set_max_string_size(1_000);
        engine.set_max_array_size(200);
        engine.set_max_map_size(100);
        engine.disable_symbol("eval");
        engine.on_print(|_| {});
        engine.on_debug(|_, _, _| {});
        register(&mut engine);
        Runner { engine, asts: HashMap::new() }
    }

    /// Run one frame of a post's script. `key` identifies the post (0 = composer preview).
    pub fn run(&mut self, key: u64, code: &str, snap: Snapshot, clicked: &HashSet<usize>) -> Output {
        let stale = match self.asts.get(&key) {
            Some((c, _)) => c != code,
            None => true,
        };
        if stale {
            let compiled = self.engine.compile(code).map_err(|e| e.to_string());
            self.asts.insert(key, (code.to_string(), compiled));
        }
        let ast = match &self.asts[&key].1 {
            Ok(a) => a,
            Err(e) => return Output { error: Some(e.clone()), ..Default::default() },
        };
        let host = Host(Rc::new(RefCell::new(HostInner {
            snap,
            clicked: clicked.clone(),
            next_btn: 0,
            cmds: vec![],
            incr: vec![],
            vote: None,
        })));
        let mut scope = Scope::new();
        scope.push("ui", host.clone());
        let res = self.engine.run_ast_with_scope(&mut scope, ast);
        let mut i = host.0.borrow_mut();
        Output {
            cmds: std::mem::take(&mut i.cmds),
            incr: std::mem::take(&mut i.incr),
            vote: i.vote.take(),
            error: res.err().map(|e| e.to_string()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn clicked(ids: &[usize]) -> HashSet<usize> {
        ids.iter().copied().collect()
    }

    #[test]
    fn poll_template_runs() {
        let mut r = Runner::new();
        let out = r.run(1, TPL_POLL, Snapshot::empty("demo"), &clicked(&[]));
        assert!(out.error.is_none(), "{:?}", out.error);
        assert_eq!(out.cmds.len(), 1 + 3 * 2);
        assert!(out.vote.is_none());
    }

    #[test]
    fn poll_click_votes() {
        let mut r = Runner::new();
        let out = r.run(1, TPL_POLL, Snapshot::empty("demo"), &clicked(&[1]));
        assert!(out.error.is_none(), "{:?}", out.error);
        assert_eq!(out.vote.as_deref(), Some("Option B"));
    }

    #[test]
    fn meter_reads_and_increments() {
        let mut r = Runner::new();
        let mut snap = Snapshot::empty("demo");
        snap.counters.insert("clicks".into(), 10);
        let out = r.run(2, TPL_METER, snap.clone(), &clicked(&[]));
        assert!(out.error.is_none(), "{:?}", out.error);
        let bar = out.cmds.iter().find_map(|c| if let Cmd::Bar(f, t) = c { Some((*f, t.clone())) } else { None });
        let (f, t) = bar.expect("bar emitted");
        assert!((f - 0.5).abs() < 1e-6);
        assert_eq!(t, "10 / 20");
        let out = r.run(2, TPL_METER, snap, &clicked(&[0]));
        assert_eq!(out.incr, vec!["clicks".to_string()]);
    }

    #[test]
    fn sandbox_blocks_abuse() {
        let mut r = Runner::new();
        for (i, bad) in ["loop { }", "import \"x\" as y;", "eval(\"1+1\")", "let s = \"a\"; loop { s += s; }"].iter().enumerate() {
            let out = r.run(100 + i as u64, bad, Snapshot::empty("demo"), &clicked(&[]));
            assert!(out.error.is_some(), "script should have been rejected: {bad}");
        }
    }

    #[test]
    fn syntax_error_is_reported_not_panicking() {
        let mut r = Runner::new();
        let out = r.run(5, "let x = ;", Snapshot::empty("demo"), &clicked(&[]));
        assert!(out.error.is_some());
        let out = r.run(5, "ui.label(\"fixed\");", Snapshot::empty("demo"), &clicked(&[]));
        assert!(out.error.is_none());
        assert_eq!(out.cmds, vec![Cmd::Label("fixed".into())]);
    }

    #[test]
    fn store_rules() {
        let dir = std::env::temp_dir().join(format!("codepost-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let mut s = Store::load(Some(dir.clone()));
        assert_eq!(s.posts().len(), 3);
        let poll_id = s.posts()[1].id;
        assert!(s.vote(poll_id, "demo", "Option A"));
        assert!(!s.vote(poll_id, "demo", "Option A"), "same vote twice is a no-op");
        assert!(s.vote(poll_id, "demo", "Option B"), "changing vote works");
        assert!(s.vote(poll_id, "sara", "Option B"));
        let snap = Snapshot::from_post(&s.posts()[1], "sara");
        assert_eq!(snap.vote_counts.get("Option B"), Some(&2));
        assert_eq!(snap.vote_counts.get("Option A"), None);
        assert_eq!(snap.my_vote, "Option B");
        assert!(!s.delete_post(poll_id, "sara"), "only the author can delete");
        // persistence round-trip
        let s2 = Store::load(Some(dir.clone()));
        assert_eq!(s2.posts()[1].votes.len(), 2);
        assert!(s.add_post("demo", "", "").is_err());
        let _ = std::fs::remove_dir_all(dir);
    }
}
