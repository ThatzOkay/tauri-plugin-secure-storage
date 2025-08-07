const COMMANDS: &[&str] = &["ping", "set_synchronize_keychain", "get_item", "set_item"];

fn main() {
  tauri_plugin::Builder::new(COMMANDS)
    .android_path("android")
    .ios_path("ios")
    .build();
}
