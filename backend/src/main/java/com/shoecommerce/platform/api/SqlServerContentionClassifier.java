package com.shoecommerce.platform.api;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class SqlServerContentionClassifier {
    private static final int DEADLOCK_VICTIM = 1205;
    private static final int LOCK_TIMEOUT = 1222;

    private SqlServerContentionClassifier() { }

    static boolean isRetryable(Throwable failure) {
        if (failure == null) return false;
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof SQLException sqlException) {
                int errorCode = sqlException.getErrorCode();
                if (errorCode == DEADLOCK_VICTIM || errorCode == LOCK_TIMEOUT) return true;
                if (sqlException.getNextException() != null) pending.add(sqlException.getNextException());
            }
            if (current.getCause() != null) pending.add(current.getCause());
        }
        return false;
    }
}
