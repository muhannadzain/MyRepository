//! egui UI (Android only). Twitter-like skeleton: account switcher, composer, feed.
use crate::core::{Cmd, Post, Runner, Snapshot, Store, MAX_CAPTION, TPL_METER, TPL_POLL};
use eframe::egui;
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

/// Local demo accounts (fully offline). Switch between them to test one-vote-per-user.
const USERS: [&str; 3] = ["demo", "sara", "omar"];

pub struct CodePostApp {
    store: Store,
    runner: Runner,
    me: String,
    caption: String,
    code: String,
    msg: String,
    /// Buttons clicked last frame, per post (scripts see them on the next run).
    clicked: HashMap<u64, HashSet<usize>>,
}

impl CodePostApp {
    pub fn new(_cc: &eframe::CreationContext<'_>, dir: Option<PathBuf>) -> Self {
        Self {
            store: Store::load(dir),
            runner: Runner::new(),
            me: USERS[0].to_string(),
            caption: String::new(),
            code: String::new(),
            msg: String::new(),
            clicked: HashMap::new(),
        }
    }

    fn compose(&mut self, ui: &mut egui::Ui) {
        ui.label(format!("Posting as @{}", self.me));
        ui.add(
            egui::TextEdit::multiline(&mut self.caption)
                .hint_text("What's happening?")
                .desired_rows(2)
                .desired_width(f32::INFINITY),
        );
        ui.horizontal(|ui| {
            if ui.button("Template: Poll").clicked() {
                self.code = TPL_POLL.to_string();
            }
            if ui.button("Template: Meter").clicked() {
                self.code = TPL_METER.to_string();
            }
            if ui.button("Clear code").clicked() {
                self.code.clear();
            }
        });
        ui.add(
            egui::TextEdit::multiline(&mut self.code)
                .code_editor()
                .hint_text("Optional code block (Rhai script)")
                .desired_rows(5)
                .desired_width(f32::INFINITY),
        );
        if !self.code.trim().is_empty() {
            ui.weak("Preview (interactions are applied after posting):");
            let code = self.code.clone();
            self.render_script(ui, 0, &code, None);
        }
        if ui.button("Post").clicked() {
            let (caption, code, me) = (self.caption.clone(), self.code.clone(), self.me.clone());
            match self.store.add_post(&me, &caption, &code) {
                Ok(()) => {
                    self.caption.clear();
                    self.code.clear();
                    self.msg.clear();
                }
                Err(e) => self.msg = e,
            }
        }
        ui.weak(format!("Caption max {MAX_CAPTION} chars"));
        if !self.msg.is_empty() {
            ui.colored_label(egui::Color32::RED, &self.msg);
        }
    }

    fn feed(&mut self, ui: &mut egui::Ui) {
        let posts: Vec<Post> = self.store.posts().iter().take(50).cloned().collect();
        let mut deletes: Vec<u64> = Vec::new();
        for p in &posts {
            ui.separator();
            ui.horizontal(|ui| {
                ui.strong(format!("@{}", p.author));
                ui.weak(format!("#{}", p.id));
                if p.author == self.me && ui.small_button("Delete").clicked() {
                    deletes.push(p.id);
                }
            });
            if !p.caption.is_empty() {
                ui.label(&p.caption);
            }
            if !p.code.trim().is_empty() {
                self.render_script(ui, p.id, &p.code, Some(p));
            }
        }
        let me = self.me.clone();
        for id in deletes {
            self.store.delete_post(id, &me);
        }
    }

    /// Runs the post's script for this frame and draws what it emitted.
    /// `post == None` means composer preview: nothing is ever written.
    fn render_script(&mut self, ui: &mut egui::Ui, key: u64, code: &str, post: Option<&Post>) {
        let clicked = self.clicked.remove(&key).unwrap_or_default();
        let snap = match post {
            Some(p) => Snapshot::from_post(p, &self.me),
            None => Snapshot::empty(&self.me),
        };
        let out = self.runner.run(key, code, snap, &clicked);

        let width = ui.available_width();
        let mut new_clicked: HashSet<usize> = HashSet::new();
        egui::Frame::group(ui.style()).show(ui, |ui| {
            if let Some(e) = &out.error {
                ui.colored_label(egui::Color32::RED, format!("Script error: {e}"));
            }
            for cmd in &out.cmds {
                match cmd {
                    Cmd::Label(t) => {
                        ui.label(t);
                    }
                    Cmd::Button(id, t) => {
                        if ui.button(t).clicked() {
                            new_clicked.insert(*id);
                        }
                    }
                    Cmd::Bar(f, t) => {
                        ui.add(egui::ProgressBar::new(*f).desired_width(width - 24.0).text(t.as_str()));
                    }
                }
            }
        });
        if !new_clicked.is_empty() {
            self.clicked.insert(key, new_clicked);
            ui.ctx().request_repaint();
        }

        if let Some(p) = post {
            let me = self.me.clone();
            let mut wrote = false;
            for k in &out.incr {
                wrote |= self.store.incr(p.id, k);
            }
            if let Some(v) = &out.vote {
                wrote |= self.store.vote(p.id, &me, v);
            }
            if wrote {
                ui.ctx().request_repaint();
            }
        }
    }
}

impl eframe::App for CodePostApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::TopBottomPanel::top("top").show(ctx, |ui| {
            ui.add_space(6.0);
            ui.horizontal(|ui| {
                ui.heading("CodePost");
                for u in USERS {
                    ui.selectable_value(&mut self.me, u.to_string(), u);
                }
            });
            ui.add_space(4.0);
        });
        egui::CentralPanel::default().show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                self.compose(ui);
                self.feed(ui);
            });
        });
    }
}
