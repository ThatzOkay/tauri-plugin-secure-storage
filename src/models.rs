use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OptionsRequest {
    pub prefixed_key: Option<String>,
    pub data: Option<String>,
    pub sync: Option<bool>,
    pub keychain_access: Option<u32>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GetItemResponse {
    pub data: Option<String>,
}