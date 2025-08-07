use keyring::Entry;
use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<SecureStorage<R>> {
    Ok(SecureStorage(app.clone()))
}

/// Access to the secure-storage APIs.
pub struct SecureStorage<R: Runtime>(AppHandle<R>);

impl<R: Runtime> SecureStorage<R> {
    pub fn set_synchronize_keychain(&self, payload: OptionsRequest) {}

    pub fn get_item(&self, app: AppHandle<R>, payload: OptionsRequest) -> crate::Result<GetItemResponse> {
        let key = payload.prefixed_key;

        if key.is_none() {
            return Err(std::io::Error::new(std::io::ErrorKind::NotFound, "Key not found").into());
        }

        let entry = Entry::new(&*app.config().product_name.clone().unwrap(), &*key.unwrap());

        let data = entry.unwrap().get_password();

        match data {
            Ok(data) => {
                Ok(
                    GetItemResponse {
                        data: Some(data)
                    }
                )
            },
            Err(_) => {
                Ok(
                    GetItemResponse {
                        data: None
                    }
                )
            },
        }
    }

    pub fn set_item(&self, app: AppHandle<R>, payload: OptionsRequest) -> crate::Result<String> {
        let key = payload.prefixed_key;

        if key.is_none() {
            return Err(std::io::Error::new(std::io::ErrorKind::NotFound, "Key not found").into());
        }

        if payload.data.is_none() {
            return Err(std::io::Error::new(std::io::ErrorKind::NotFound, "Data not found").into());
        }

        let entry = Entry::new(&*app.config().product_name.clone().unwrap(), &*key.unwrap());

        let payload_data = payload.data.clone().unwrap();

        let result = entry.unwrap().set_password(&*payload.data.unwrap());

        match result {
            Ok(_) => {
                Ok(payload_data)
            }
            Err(e) => {
                println!("{}", e);
                Err(std::io::Error::new(std::io::ErrorKind::NotFound, "Data not found").into())
            }
        }
    }
}
