#ifndef BLOWHARD_KEYCHAIN_H
#define BLOWHARD_KEYCHAIN_H

#include <CoreFoundation/CoreFoundation.h>
#include <Security/Security.h>
#include <stdint.h>
#include <string.h>

static inline CFStringRef BlowhardKeychainString(const char *value) {
    return CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8);
}

static inline CFMutableDictionaryRef BlowhardKeychainDictionary(void) {
    return CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        0,
        &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks
    );
}

static inline CFMutableDictionaryRef BlowhardKeychainQuery(
    CFStringRef service,
    CFStringRef account
) {
    CFMutableDictionaryRef query = BlowhardKeychainDictionary();
    if (query == NULL) {
        return NULL;
    }
    CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword);
    CFDictionarySetValue(query, kSecAttrService, service);
    CFDictionarySetValue(query, kSecAttrAccount, account);
    CFDictionarySetValue(query, kSecAttrSynchronizable, kCFBooleanFalse);
    return query;
}

static inline int32_t BlowhardKeychainRead(
    const char *service_value,
    const char *account_value,
    uint8_t *output,
    int32_t capacity
) {
    if (service_value == NULL || account_value == NULL || output == NULL || capacity <= 0) {
        return errSecParam;
    }

    CFStringRef service = BlowhardKeychainString(service_value);
    CFStringRef account = BlowhardKeychainString(account_value);
    if (service == NULL || account == NULL) {
        if (service != NULL) CFRelease(service);
        if (account != NULL) CFRelease(account);
        return errSecAllocate;
    }

    CFMutableDictionaryRef query = BlowhardKeychainQuery(service, account);
    CFRelease(service);
    CFRelease(account);
    if (query == NULL) {
        return errSecAllocate;
    }

    CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue);
    CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne);

    CFTypeRef result = NULL;
    OSStatus status = SecItemCopyMatching(query, &result);
    CFRelease(query);
    if (status == errSecItemNotFound) {
        return 0;
    }
    if (status != errSecSuccess) {
        if (result != NULL) CFRelease(result);
        return status;
    }
    if (result == NULL || CFGetTypeID(result) != CFDataGetTypeID()) {
        if (result != NULL) CFRelease(result);
        return errSecDecode;
    }

    CFDataRef data = (CFDataRef) result;
    CFIndex length = CFDataGetLength(data);
    if (length <= 0 || length > capacity || length > INT32_MAX) {
        CFRelease(result);
        return errSecDecode;
    }

    const UInt8 *bytes = CFDataGetBytePtr(data);
    if (bytes == NULL) {
        CFRelease(result);
        return errSecDecode;
    }
    memcpy(output, bytes, (size_t) length);
    CFRelease(result);
    return (int32_t) length;
}

static inline OSStatus BlowhardKeychainUpdate(
    CFDictionaryRef query,
    CFDataRef secret
) {
    CFMutableDictionaryRef attributes = BlowhardKeychainDictionary();
    if (attributes == NULL) {
        return errSecAllocate;
    }
    CFDictionarySetValue(attributes, kSecValueData, secret);
    CFDictionarySetValue(
        attributes,
        kSecAttrAccessible,
        kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    );
    OSStatus status = SecItemUpdate(query, attributes);
    CFRelease(attributes);
    return status;
}

static inline int32_t BlowhardKeychainReplace(
    const char *service_value,
    const char *account_value,
    const uint8_t *secret_bytes,
    int32_t secret_length
) {
    if (
        service_value == NULL ||
        account_value == NULL ||
        secret_bytes == NULL ||
        secret_length <= 0
    ) {
        return errSecParam;
    }

    CFStringRef service = BlowhardKeychainString(service_value);
    CFStringRef account = BlowhardKeychainString(account_value);
    CFDataRef secret = CFDataCreate(kCFAllocatorDefault, secret_bytes, secret_length);
    if (service == NULL || account == NULL || secret == NULL) {
        if (service != NULL) CFRelease(service);
        if (account != NULL) CFRelease(account);
        if (secret != NULL) CFRelease(secret);
        return errSecAllocate;
    }

    CFMutableDictionaryRef query = BlowhardKeychainQuery(service, account);
    if (query == NULL) {
        CFRelease(service);
        CFRelease(account);
        CFRelease(secret);
        return errSecAllocate;
    }

    OSStatus status = BlowhardKeychainUpdate(query, secret);
    if (status == errSecItemNotFound) {
        CFDictionarySetValue(query, kSecValueData, secret);
        CFDictionarySetValue(
            query,
            kSecAttrAccessible,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        );
        status = SecItemAdd(query, NULL);
        if (status == errSecDuplicateItem) {
            CFDictionaryRemoveValue(query, kSecValueData);
            CFDictionaryRemoveValue(query, kSecAttrAccessible);
            status = BlowhardKeychainUpdate(query, secret);
        }
    }

    CFRelease(query);
    CFRelease(service);
    CFRelease(account);
    CFRelease(secret);
    return status;
}

static inline int32_t BlowhardKeychainDelete(
    const char *service_value,
    const char *account_value
) {
    if (service_value == NULL || account_value == NULL) {
        return errSecParam;
    }

    CFStringRef service = BlowhardKeychainString(service_value);
    CFStringRef account = BlowhardKeychainString(account_value);
    if (service == NULL || account == NULL) {
        if (service != NULL) CFRelease(service);
        if (account != NULL) CFRelease(account);
        return errSecAllocate;
    }

    CFMutableDictionaryRef query = BlowhardKeychainQuery(service, account);
    CFRelease(service);
    CFRelease(account);
    if (query == NULL) {
        return errSecAllocate;
    }

    OSStatus status = SecItemDelete(query);
    CFRelease(query);
    return status;
}

#endif
