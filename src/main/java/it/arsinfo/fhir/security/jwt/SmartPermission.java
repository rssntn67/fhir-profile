package it.arsinfo.fhir.security.jwt;

import java.util.EnumSet;
import java.util.Set;

/**
 * SMART on FHIR v2 permission codes (c/r/u/d/s) with legacy read/write support.
 */
public enum SmartPermission {
    CREATE('c'),
    READ('r'),
    UPDATE('u'),
    DELETE('d'),
    SEARCH('s');

    public final char code;

    SmartPermission(char code) {
        this.code = code;
    }

    /** Parse a SMART v2 permissions string such as "cruds" or "ru". */
    public static Set<SmartPermission> fromV2String(String perms) {
        if (perms == null || perms.isBlank()) {
            return EnumSet.noneOf(SmartPermission.class);
        }
        Set<SmartPermission> result = EnumSet.noneOf(SmartPermission.class);
        for (char c : perms.toLowerCase().toCharArray()) {
            switch (c) {
                case 'c' -> result.add(CREATE);
                case 'r' -> result.add(READ);
                case 'u' -> result.add(UPDATE);
                case 'd' -> result.add(DELETE);
                case 's' -> result.add(SEARCH);
                default  -> throw new IllegalArgumentException("Unknown SMART permission code: " + c);
            }
        }
        return result;
    }

    /** SMART v1 "read" → read + search. */
    public static Set<SmartPermission> fromLegacyRead() {
        return EnumSet.of(READ, SEARCH);
    }

    /** SMART v1 "write" → create + update. */
    public static Set<SmartPermission> fromLegacyWrite() {
        return EnumSet.of(CREATE, UPDATE);
    }

    /** Wildcard (*) → all permissions. */
    public static Set<SmartPermission> all() {
        return EnumSet.allOf(SmartPermission.class);
    }
}
