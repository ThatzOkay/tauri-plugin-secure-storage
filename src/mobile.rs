use serde::de::DeserializeOwned;
use tauri::{
  plugin::{PluginApi, PluginHandle},
  AppHandle, Runtime,
};
use tauri::webview::PageLoadPayload;
use crate::models::*;

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_secure_storage);

// initializes the Kotlin or Swift plugin classes
pub fn init<R: Runtime, C: DeserializeOwned>(
  _app: &AppHandle<R>,
  api: PluginApi<R, C>,
) -> crate::Result<SecureStorage<R>> {
  #[cfg(target_os = "android")]
  let handle = api.register_android_plugin("nl.thatzokay.secureStorage", "SecureStoragePlugin")?;
  #[cfg(target_os = "ios")]
  let handle = api.register_ios_plugin(init_plugin_secure_storage)?;
  Ok(SecureStorage(handle))
}

/// Access to the secure-storage APIs.
pub struct SecureStorage<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> SecureStorage<R> {
   pub fn set_synchronize_keychain(&self, payload: OptionsRequest) {
       self.0.run_mobile_plugin::<()>("setSynchronizeKeychain", payload).expect("Failed to run method");
   }

    pub fn get_item(&self, app: AppHandle<R>, payload: OptionsRequest) -> crate::Result<GetItemResponse> {
        self.0.run_mobile_plugin("getItem", payload)
            .map_err(Into::into)
    }

    pub fn set_item(&self, app: AppHandle<R>, payload: OptionsRequest) -> crate::Result<Option<String>> {
        self.0.run_mobile_plugin("setItem", payload)
            .map_err(Into::into)
    }
}
