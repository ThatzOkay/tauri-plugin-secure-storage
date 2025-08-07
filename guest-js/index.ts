import { invoke } from '@tauri-apps/api/core'
import {DataType, KeychainAccess, SecureStoragePlugin, StorageError, StorageErrorType} from "./defenition";
import {platform} from "@tauri-apps/plugin-os";

function isStorageErrorType(
    value: string | undefined,
): value is keyof typeof StorageErrorType {
    return value !== undefined && Object.keys(StorageErrorType).includes(value)
}

class SecureStorage implements SecureStoragePlugin {

    protected prefix = 'tauri-storage_'
    protected sync = false
    protected access = KeychainAccess.whenUnlocked

    async setSynchronize(sync: boolean): Promise<void> {
        this.sync = sync;

        if (platform() === 'ios') {
            return this.setSynchronizeKeychain({ sync })
        }

        // no-op on other platforms
    }

    async getSynchronize(): Promise<boolean> {
        return this.sync
    }

    // @native
    protected setSynchronizeKeychain(options: {
        sync: boolean
    }): Promise<void> {
        return invoke('plugin:secure-storage|set_synchronize_keychain', options)
    }

    async setDefaultKeychainAccess(access: KeychainAccess): Promise<void> {
        this.access = access
    }

    protected async tryOperation<T>(operation: () => Promise<T>): Promise<T> {
        try {
            // Ensure that only one operation is in progress at a time.
            return await operation()
        } catch (error) {
            // Native calls which reject will throw a CapacitorException with a code.
            // We want to convert these to StorageErrors.
            if (
                error instanceof Error &&
                isStorageErrorType(error.message)
            ) {
                throw new StorageError(error.message, StorageErrorType.unknownError)
            }

            throw error
        }
    }

    async get(
        key: string,
        convertDate = true,
        sync?: boolean,
    ): Promise<DataType | null> {
        if (key) {
            const { data } = await this.tryOperation(async () =>
                this.internalGetItem({
                    prefixedKey: this.prefixedKey(key),
                    sync: sync ?? this.sync,
                }),
            )

            if (data === null) {
                return null
            }

            if (convertDate) {
                const date = parseISODate(data)

                if (date) {
                    return date
                }
            }

            try {
                // eslint-disable-next-line @typescript-eslint/no-unsafe-type-assertion
                return JSON.parse(data) as DataType
            } catch {
                throw new StorageError('Invalid data', StorageErrorType.invalidData)
            }
        }

        return SecureStorage.missingKey()
    }

    async getItem(key: string): Promise<string | null> {
        if (key) {
            const { data } = await this.tryOperation(async () =>
                this.internalGetItem({
                    prefixedKey: this.prefixedKey(key),
                    sync: this.sync,
                }),
            )

            return data
        }

        return null
    }

    // @native
    protected internalGetItem(options: {
        prefixedKey: string
        sync: boolean
    }): Promise<{ data: string | null }> {
        return invoke<{ data: string | null}>('plugin:secure-storage|get_item', {
            payload: options
        })
    }

    async set(
        key: string,
        data: DataType,
        convertDate = true,
        sync?: boolean,
        access?: KeychainAccess,
    ): Promise<void> {
        if (key) {
            let convertedData = data

            if (convertDate && data instanceof Date) {
                convertedData = data.toISOString()
            }

            return this.tryOperation(async () =>
                this.internalSetItem({
                    prefixedKey: this.prefixedKey(key),
                    data: JSON.stringify(convertedData),
                    sync: sync ?? this.sync,
                    access: access ?? this.access,
                }),
            )
        }

        return SecureStorage.missingKey()
    }

    async setItem(key: string, value: string): Promise<void> {
        if (key) {
            return this.tryOperation(async () =>
                this.internalSetItem({
                    prefixedKey: this.prefixedKey(key),
                    data: value,
                    sync: this.sync,
                    access: this.access,
                }),
            )
        }

        return SecureStorage.missingKey()
    }

    // @native
    protected internalSetItem(options: {
        prefixedKey: string
        data: string
        sync: boolean
        access: KeychainAccess
    }): Promise<void> {
        return invoke('plugin:secure-storage|set_item', {
            payload: options
        })
    }

    async remove(key: string, sync?: boolean): Promise<boolean> {
        if (key) {
            const { success } = await this.tryOperation(async () =>
                this.internalRemoveItem({
                    prefixedKey: this.prefixedKey(key),
                    sync: sync ?? this.sync,
                }),
            )

            return success
        }

        return SecureStorage.missingKey()
    }

    async removeItem(key: string): Promise<void> {
        if (key) {
            await this.tryOperation(async () =>
                this.internalRemoveItem({
                    prefixedKey: this.prefixedKey(key),
                    sync: this.sync,
                }),
            )

            return
        }

        SecureStorage.missingKey()
    }

    // @native
    protected internalRemoveItem(options: {
        prefixedKey: string
        sync: boolean
    }): Promise<{ success: boolean }> {
        return invoke<{ success: boolean }>('plugin:secure-storage|remove_item', options)
    }

    async clear(sync?: boolean): Promise<void> {
        return invoke('plugin:secure-storage|clear', { sync: sync })
    }

    // @native
    protected clearItemsWithPrefix(options: {
        prefix: string
        sync: boolean
    }): Promise<void> {
        return invoke('plugin:secure-storage|clear_item_with_prefix', options)
    }

    async keys(sync?: boolean): Promise<string[]> {
        const { keys } = await this.tryOperation(async () =>
            this.getPrefixedKeys({
                prefix: this.prefix,
                sync: sync ?? this.sync,
            }),
        )

        const prefixLength = this.prefix.length
        return keys.map((key) => key.slice(prefixLength))
    }

    // @native
    protected getPrefixedKeys(options: {
        prefix: string
        sync: boolean
    }): Promise<{ keys: string[] }> {
        return invoke<{ keys: string[] }>('plugin:secure-storage|get_prefixed_keys', options)
    }

    async getKeyPrefix(): Promise<string> {
        return this.prefix
    }

    async setKeyPrefix(prefix: string): Promise<void> {
        this.prefix = prefix
    }

    protected prefixedKey(key: string): string {
        return this.prefix + key
    }

    protected static missingKey(): never {
        throw new StorageError('No key provided', StorageErrorType.missingKey)
    }
}

// RegExp to match an ISO 8601 date string in the form YYYY-MM-DDTHH:mm:ss.sssZ
const isoDateRE =
    /^"(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2}).(\d{3})Z"$/u

function parseISODate(isoDate: string): Date | null {
    const match = isoDateRE.exec(isoDate)

    if (match) {
        const year = Number.parseInt(match[1], 10)
        const month = Number.parseInt(match[2], 10) - 1 // month is zero-based
        const day = Number.parseInt(match[3], 10)
        const hour = Number.parseInt(match[4], 10)
        const minute = Number.parseInt(match[5], 10)
        const second = Number.parseInt(match[6], 10)
        const millis = Number.parseInt(match[7], 10)
        const epochTime = Date.UTC(year, month, day, hour, minute, second, millis)
        return new Date(epochTime)
    }

    return null
}

const secureStorage = new SecureStorage();

export { secureStorage }