pub mod core;

#[cfg(target_os = "android")]
mod app;

#[cfg(target_os = "android")]
#[no_mangle]
fn android_main(android_app: winit::platform::android::activity::AndroidApp) {
    android_logger::init_once(android_logger::Config::default().with_max_level(log::LevelFilter::Info));
    let dir = android_app.internal_data_path();
    let options = eframe::NativeOptions { android_app: Some(android_app), ..Default::default() };
    let res = eframe::run_native(
        "CodePost",
        options,
        Box::new(move |cc| Ok(Box::new(app::CodePostApp::new(cc, dir)))),
    );
    if let Err(e) = res {
        log::error!("eframe error: {e}");
    }
}
