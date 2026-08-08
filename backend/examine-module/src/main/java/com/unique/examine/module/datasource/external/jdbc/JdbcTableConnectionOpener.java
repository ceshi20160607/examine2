package com.unique.examine.module.datasource.external.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
interface JdbcTableConnectionOpener {
    Connection open(String url, String username, String password)
            throws SQLException;
}
