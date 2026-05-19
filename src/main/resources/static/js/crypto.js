window.OneLineCrypto = (() => {
    const VERSION = 0x01;
    const NONCE_BYTES = 12;
    const KEY_BITS = 256;
    const INFO = new TextEncoder().encode('oneline.message.v1');

    const chatIdSalt = (chatId) => {
        const view = new DataView(new ArrayBuffer(8));
        const big = BigInt(chatId);
        view.setBigInt64(0, big, false);
        return new Uint8Array(view.buffer);
    };

    const base64Encode = (bytes) => {
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    };

    const base64Decode = (str) => {
        const binary = atob(str);
        const out = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            out[i] = binary.charCodeAt(i);
        }
        return out;
    };

    const concat = (...arrays) => {
        let len = 0;
        for (const a of arrays) {
            len += a.length;
        }
        const result = new Uint8Array(len);
        let offset = 0;
        for (const a of arrays) {
            result.set(a, offset);
            offset += a.length;
        }
        return result;
    };

    const deriveKey = async (secret, chatId) => {
        const ikm = new TextEncoder().encode(secret);
        const baseKey = await crypto.subtle.importKey(
            'raw',
            ikm,
            { name: 'HKDF' },
            false,
            ['deriveKey']
        );
        return crypto.subtle.deriveKey(
            {
                name: 'HKDF',
                hash: 'SHA-256',
                salt: chatIdSalt(chatId),
                info: INFO,
            },
            baseKey,
            { name: 'AES-GCM', length: KEY_BITS },
            false,
            ['encrypt', 'decrypt']
        );
    };

    const encrypt = async (key, plaintext) => {
        const nonce = crypto.getRandomValues(new Uint8Array(NONCE_BYTES));
        const ciphertext = new Uint8Array(await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv: nonce, tagLength: 128 }, key, new TextEncoder().encode(plaintext))
        );
        return base64Encode(concat(new Uint8Array([VERSION]), nonce, ciphertext));
    };

    const decrypt = async (key, encoded) => {
        const bytes = typeof encoded === 'string' ? base64Decode(encoded) : new Uint8Array(encoded);
        if (bytes.length < 1 + NONCE_BYTES + 16) {
            throw new Error('Ciphertext too short');
        }
        if (bytes[0] !== VERSION) {
            throw new Error('Unsupported message version: ' + bytes[0]);
        }
        const nonce = bytes.slice(1, 1 + NONCE_BYTES);
        const ciphertext = bytes.slice(1 + NONCE_BYTES);
        const plain = new Uint8Array(await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv: nonce, tagLength: 128 }, key, ciphertext)
        );
        return new TextDecoder().decode(plain);
    };

    return { deriveKey, encrypt, decrypt, base64Encode, base64Decode };
})();
