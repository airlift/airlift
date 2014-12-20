/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.dbpool;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import javax.validation.constraints.NotNull;

/**
 * Configuration for {@link H2EmbeddedDataSource}.
 * <p>
 * The configuration options can be chained as follows:
 * <pre>
 * {@code
 *     H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
 *             .setUsername("username")
 *             .setPassword("password")
 *             .setMaxConnections(20)
 *             .setMaxConnectionWait(new Duration(20, TimeUnit.MILLISECONDS)),
 *             .setFilename(fileName)
 *             .setInitScript("src/test/db/h2.ddl");
 * }
 * </pre>
 */
public class H2EmbeddedDataSourceConfig extends ManagedDataSourceConfig<H2EmbeddedDataSourceConfig>
{
    public static enum AllowLiterals
    {
        NONE, NUMBERS, ALL
    }

    public static enum CompressLob
    {
        NO, LZF, DEFLATE
    }

    public static enum Cipher
    {
        NONE, AES, XTEA
    }

    private String filename;
    private String filePassword;
    private String initScript;
    private AllowLiterals allowLiterals = AllowLiterals.ALL;
    private CompressLob compressLob = CompressLob.LZF;
    private Cipher cipher = Cipher.NONE;
    private int cacheSize = 16384;
    private long maxLengthInplaceLob = 1024;
    private long maxMemoryRows = 10000;
    private boolean mvccEnabled = true;

    /**
     * Database filename
     */
    @NotNull
    public String getFilename()
    {
        return filename;
    }

    @Config("db.filename")
    public H2EmbeddedDataSourceConfig setFilename(String filename)
    {
        this.filename = filename;
        return this;
    }

    /**
     * Password for the encrypted database file
     */
    public String getFilePassword()
    {
        return filePassword;
    }

    @Config("db.file-password")
    public H2EmbeddedDataSourceConfig setFilePassword(String filePassword)
    {
        this.filePassword = filePassword;
        return this;
    }

    /**
     * Initialization script run at startup
     */
    public String getInitScript()
    {
        return initScript;
    }

    @Config("db.init-script")
    public H2EmbeddedDataSourceConfig setInitScript(String initScript)
    {
        this.initScript = initScript;
        return this;
    }

    /**
     * This setting can help solve the SQL injection problem. By default, text
     * and number literals are allowed in SQL statements. However, this enables
     * SQL injection if the application dynamically builds SQL statements. SQL
     * injection is not possible if user data is set using parameters ('?').
     * <p>
     * NONE means literals of any kind are not allowed, only parameters and
     * constants are allowed. NUMBERS mean only numerical and boolean literals
     * are allowed. ALL means all literals are allowed (default).
     */
    public AllowLiterals getAllowLiterals()
    {
        return allowLiterals;
    }

    @Config("db.allow-literals")
    public H2EmbeddedDataSourceConfig setAllowLiterals(AllowLiterals allowLiterals)
    {
        if (allowLiterals == null) {
            throw new NullPointerException("allowLiterals is null");
        }
        this.allowLiterals = allowLiterals;
        return this;
    }

    /**
     * Sets the compression algorithm for BLOB and CLOB data. Compression is
     * usually slower, but needs less disk space. LZF is faster but uses more space.
     * <p>
     * Allowed values are "NO", "LZF" and "DEFLATE"
     */
    public CompressLob getCompressLob()
    {
        return compressLob;
    }

    @Config("db.compress-lob")
    public H2EmbeddedDataSourceConfig setCompressLob(CompressLob compressLob)
    {
        if (compressLob == null) {
            throw new NullPointerException("compressLob is null");
        }
        this.compressLob = compressLob;
        return this;
    }

    /**
     * Sets the cipher algorithm to encrypt the database file.
     */
    public Cipher getCipher()
    {
        return cipher;
    }

    @Config("db.cipher")
    public H2EmbeddedDataSourceConfig setCipher(Cipher cipher)
    {
        if (cipher == null) {
            throw new NullPointerException("cipher is null");
        }
        this.cipher = cipher;
        return this;
    }

    /**
     * Sets the size of the cache in KB (each KB being 1024 bytes) for the
     * current database. The default value is 16384 (16 MB). The value is
     * rounded to the next higher power of two. Depending on the virtual
     * machine, the actual memory required may be higher.
     */
    // todo we should have a typed value class for this
    public int getCacheSize()
    {
        return cacheSize;
    }

    @Config("db.cache-size")
    public H2EmbeddedDataSourceConfig setCacheSize(int cacheSize)
    {
        this.cacheSize = cacheSize;
        return this;
    }

    /**
     * Sets the maximum size of an in-place LOB object. LOB objects larger that
     * this size are stored in a separate file, otherwise stored directly in the
     * database (in-place). The default max size is 1024. This setting has no
     * effect for in-memory databases.
     */
    public long getMaxLengthInplaceLob()
    {
        return maxLengthInplaceLob;
    }

    @Config("db.inplace.lob.length.max")
    public H2EmbeddedDataSourceConfig setMaxLengthInplaceLob(long maxLengthInplaceLob)
    {
        this.maxLengthInplaceLob = maxLengthInplaceLob;
        return this;
    }

    /**
     * The maximum number of rows in a result set that are kept in-memory. If
     * more rows are read, then the rows are buffered to disk. The default value
     * is 10000.
     */
    public long getMaxMemoryRows()
    {
        return maxMemoryRows;
    }

    @Config("db.rows.memory.max")
    public H2EmbeddedDataSourceConfig setMaxMemoryRows(long maxMemoryRows)
    {
        this.maxMemoryRows = maxMemoryRows;
        return this;
    }

    public boolean isMvccEnabled()
    {
        return mvccEnabled;
    }

    @Config("db.mvcc.enabled")
    @ConfigDescription("Enable MVCC mode for higher concurrency")
    public H2EmbeddedDataSourceConfig setMvccEnabled(boolean mvccEnabled)
    {
        this.mvccEnabled = mvccEnabled;
        return this;
    }
}
