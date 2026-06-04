globalThis.OneLineCrypto = (() => {
    const VERSION = 0x01;
    const NONCE_BYTES = 12;
    const KEY_BITS = 256;
    const INFO = new TextEncoder().encode('oneline.message.v1');
    const AUTH_INFO = new TextEncoder().encode('oneline.auth.v1');
    const SECRET_BYTES = 32;

    const chatIdSalt = (chatId) => {
        const view = new DataView(new ArrayBuffer(8));
        const big = BigInt(chatId);
        view.setBigInt64(0, big, false);
        return new Uint8Array(view.buffer);
    };

    const base64Encode = (bytes) => {
        let binary = '';
        for (const b of bytes) {
            binary += String.fromCodePoint(b);
        }
        return btoa(binary);
    };

    const base64Decode = (str) => {
        const binary = atob(str);
        const out = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            out[i] = binary.codePointAt(i);
        }
        return out;
    };

    const base64UrlEncode = (bytes) => {
        const s = base64Encode(bytes).replaceAll('+', '-').replaceAll('/', '_');
        const padStart = s.indexOf('=');
        return padStart < 0 ? s : s.slice(0, padStart);
    };
    const randomSecret = () => base64UrlEncode(crypto.getRandomValues(new Uint8Array(SECRET_BYTES)));
    const deriveAuthToken = async (secret) => {
        const ikm = new TextEncoder().encode(secret);
        const baseKey = await crypto.subtle.importKey('raw', ikm, { name: 'HKDF' }, false, ['deriveBits']);
        const bits = await crypto.subtle.deriveBits(
            { name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(0), info: AUTH_INFO },
            baseKey,
            256);
        return base64UrlEncode(new Uint8Array(bits));
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

    const importRawKey = (raw) => crypto.subtle.importKey('raw', raw, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']);

    const randomRawKey = () => crypto.getRandomValues(new Uint8Array(KEY_BITS / 8));

    const encryptBytes = async (key, data) => {
        const nonce = crypto.getRandomValues(new Uint8Array(NONCE_BYTES));
        const ciphertext = new Uint8Array(await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv: nonce, tagLength: 128 }, key, data)
        );
        return concat(new Uint8Array([VERSION]), nonce, ciphertext);
    };

    const decryptBytes = async (key, bytes) => {
        const buf = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
        if (buf.length < 1 + NONCE_BYTES + 16) {
            throw new Error('Ciphertext too short');
        }
        if (buf[0] !== VERSION) {
            throw new Error('Unsupported version: ' + buf[0]);
        }
        const nonce = buf.slice(1, 1 + NONCE_BYTES);
        const ciphertext = buf.slice(1 + NONCE_BYTES);
        return new Uint8Array(await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv: nonce, tagLength: 128 }, key, ciphertext)
        );
    };

    return {
        randomSecret, deriveAuthToken, deriveKey, encrypt, decrypt, base64Encode, base64Decode,
        importRawKey, randomRawKey, encryptBytes, decryptBytes,
    };
})();
