const COMMANDS: &[&str] = &["set_synchronize_keychain", "get_item", "set_item", "remove_item"];

fn main() {
  tauri_plugin::Builder::new(COMMANDS)
    .android_path("android")
    .ios_path("ios")
    .build();
}
