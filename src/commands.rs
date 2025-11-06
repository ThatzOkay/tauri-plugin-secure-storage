use tauri::{AppHandle, command, Runtime};

use crate::models::*;
use crate::Result;
use crate::SecureStorageExt;

#[command]
pub(crate) async fn get_item<R: Runtime>(app: AppHandle<R>, payload: OptionsRequest) -> Result<GetItemResponse> { app.secure_storage().get_item(app.clone(), payload) }

#[command]
pub(crate) async fn set_item<R: Runtime>(app: AppHandle<R>, payload: OptionsRequest) -> Result<Option<String>> { app.secure_storage().set_item(app.clone(), payload) }

#[command]
pub(crate) async fn remove_item<R: Runtime>(app: AppHandle<R>, payload: OptionsRequest) -> Result<bool> { app.secure_storage().remove_item(app.clone(), payload) }