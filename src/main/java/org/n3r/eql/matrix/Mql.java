package org.n3r.eql.matrix;

import org.n3r.diamond.client.DiamondMiner;
import org.n3r.eql.Eql;
import org.n3r.eql.config.EqlConfig;
import org.n3r.eql.config.EqlConfigCache;
import org.n3r.eql.config.EqlPropertiesConfig;
import org.n3r.eql.ex.EqlExecuteException;
import org.n3r.eql.map.EqlRun;
import org.n3r.eql.matrix.sqlparser.MatrixSqlParserUtils;
import org.n3r.eql.param.EqlParamsBinder;
import org.n3r.eql.parser.EqlBlock;
import org.n3r.eql.util.EqlUtils;

import java.sql.SQLException;
import java.util.Properties;

public class Mql extends Eql {
    public Mql() {
        super(autoDetect("config"), 4);
    }

    public Mql(String key) {
        super(autoDetect(key), 4);
    }

    public Mql(EqlConfig config) {
        super(config, 4);
    }

    private static EqlConfig autoDetect(String connName) {
        String classPath = "eql/eql-matrix.properties";
        if (EqlUtils.classResourceExists(classPath)) {
            return EqlConfigCache.getEqlConfig("matrix");
        }

        Properties eqlConfig = DiamondMiner.getProperties("eql.matrix", connName);
        return new EqlPropertiesConfig(eqlConfig);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(String... directSqls) {
        if (batch != null) throw new EqlExecuteException("batch is not supported");

        checkPreconditions(directSqls);

        newExecutionContext();
        Object ret = null;
        try {
            if (directSqls.length > 0) eqlBlock = new EqlBlock();

            eqlRuns = eqlBlock.createEqlRuns(eqlConfig, executionContext, params, dynamics, directSqls);
            if (eqlRuns.size() > 1) throw new EqlExecuteException("multiple sqls are not supported");

            for (EqlRun eqlRun : eqlRuns) {
                currRun = eqlRun;
                new EqlParamsBinder().preparBindParams(currRun);
                MatrixSqlParserUtils.parse(eqlConfig, eqlRun);
                tranStart();
                getConn();

                ret = runEql();
                updateLastResultToExecutionContext(ret);
                currRun.setResult(ret);
            }

            tranCommit();
        } catch (SQLException e) {
            logger.error("sql exception", e);
            throw new EqlExecuteException("exec sql failed["
                    + currRun.getPrintSql() + "]" + e.getMessage());
        } finally {
            resetState();
            close();
        }

        return (T) ret;
    }
}
