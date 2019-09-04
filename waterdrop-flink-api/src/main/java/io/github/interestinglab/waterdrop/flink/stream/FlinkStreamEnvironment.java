package io.github.interestinglab.waterdrop.flink.stream;

import com.typesafe.config.Config;
import io.github.interestinglab.waterdrop.env.RuntimeEnv;
import io.github.interestinglab.waterdrop.flink.util.ConfigKeyName;
import io.github.interestinglab.waterdrop.flink.util.EnvironmentUtil;
import io.github.interestinglab.waterdrop.plugin.CheckResult;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.StreamQueryConfig;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author mr_xiong
 * @date 2019-08-12 22:58
 * @description
 */
public class FlinkStreamEnvironment implements RuntimeEnv {

    private static final Logger LOG = LoggerFactory.getLogger(FlinkStreamEnvironment.class);

    private Config config;

    private StreamExecutionEnvironment environment;

    private StreamTableEnvironment tableEnvironment;

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public CheckResult checkConfig() {
        return EnvironmentUtil.checkRestartStrategy(config);
    }

    @Override
    public void prepare() {
        createEnvironment();
        createStreamTableEnvironment();
    }

    public StreamExecutionEnvironment getEnvironment() {
        return environment;
    }

    public StreamTableEnvironment getTableEnvironment() {
        return tableEnvironment;
    }

    private void createStreamTableEnvironment() {
        tableEnvironment = StreamTableEnvironment.create(getEnvironment());
        StreamQueryConfig queryConfig = tableEnvironment.queryConfig();
        if (config.hasPath(ConfigKeyName.MAX_STATE_RETENTION_TIME) && config.hasPath(ConfigKeyName.MIN_STATE_RETENTION_TIME)){
            long max = config.getLong(ConfigKeyName.MAX_STATE_RETENTION_TIME);
            long min = config.getLong(ConfigKeyName.MIN_STATE_RETENTION_TIME);
            queryConfig.withIdleStateRetentionTime(Time.seconds(min),Time.seconds(max));
        }
    }

    private void createEnvironment() {
        environment = StreamExecutionEnvironment.getExecutionEnvironment();

        setTimeCharacteristic();

        setCheckpoint();

        EnvironmentUtil.setRestartStrategy(config,environment);

        if (config.hasPath(ConfigKeyName.BUFFER_TIMEOUT_MILLIS)) {
            long timeout = config.getLong(ConfigKeyName.BUFFER_TIMEOUT_MILLIS);
            environment.setBufferTimeout(timeout);
        }

        if (config.hasPath(ConfigKeyName.PARALLELISM)) {
            int parallelism = config.getInt(ConfigKeyName.PARALLELISM);
            environment.setParallelism(parallelism);
        }

        if (config.hasPath(ConfigKeyName.MAX_PARALLELISM)) {
            int max = config.getInt(ConfigKeyName.MAX_PARALLELISM);
            environment.setMaxParallelism(max);
        }


    }

    private void setTimeCharacteristic() {
        if (config.hasPath(ConfigKeyName.TIME_CHARACTERISTIC)) {
            String timeType = config.getString(ConfigKeyName.TIME_CHARACTERISTIC);
            switch (timeType.toLowerCase()) {
                case "event-time":
                    environment.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
                    break;
                case "ingestion-time":
                    environment.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime);
                    break;
                case "processing-time":
                    environment.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime);
                    break;
                default:
                    LOG.warn("set time-characteristic failed, unknown time-characteristic [{}],only support event-time,ingestion-time,processing-time", timeType);
            }
        }
    }


    private void setCheckpoint() {
        if (config.hasPath(ConfigKeyName.CHECKPOINT_INTERVAL)) {
            CheckpointConfig checkpointConfig = environment.getCheckpointConfig();
            long interval = config.getLong(ConfigKeyName.CHECKPOINT_INTERVAL);
            environment.enableCheckpointing(interval);

            if (config.hasPath(ConfigKeyName.CHECKPOINT_MODE)) {
                String mode = config.getString(ConfigKeyName.CHECKPOINT_MODE);
                switch (mode.toLowerCase()) {
                    case "exactly-once":
                        checkpointConfig.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
                        break;
                    case "at-least-once":
                        checkpointConfig.setCheckpointingMode(CheckpointingMode.AT_LEAST_ONCE);
                        break;
                    default:
                        LOG.warn("set checkpoint.mode failed, unknown checkpoint.mode [{}],only support exactly-once,at-least-once", mode);
                }
            }

            if (config.hasPath(ConfigKeyName.CHECKPOINT_TIMEOUT)) {
                long timeout = config.getLong(ConfigKeyName.CHECKPOINT_TIMEOUT);
                checkpointConfig.setCheckpointTimeout(timeout);
            }

            if (config.hasPath(ConfigKeyName.CHECKPOINT_DATA_URI)) {
                String uri = config.getString(ConfigKeyName.CHECKPOINT_DATA_URI);
                StateBackend backend = new FsStateBackend(uri);
                environment.setStateBackend(backend);
            }

            if (config.hasPath(ConfigKeyName.MAX_CONCURRENT_CHECKPOINTS)) {
                int max = config.getInt(ConfigKeyName.MAX_CONCURRENT_CHECKPOINTS);
                checkpointConfig.setMaxConcurrentCheckpoints(max);
            }

            if (config.hasPath(ConfigKeyName.CHECKPOINT_CLEANUP_MODE)) {
                boolean cleanup = config.getBoolean(ConfigKeyName.CHECKPOINT_CLEANUP_MODE);
                if (cleanup) {
                    checkpointConfig.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION);
                } else {
                    checkpointConfig.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

                }
            }

            if (config.hasPath(ConfigKeyName.MIN_PAUSE_BETWEEN_CHECKPOINTS)) {
                long minPause = config.getLong(ConfigKeyName.MIN_PAUSE_BETWEEN_CHECKPOINTS);
                checkpointConfig.setMinPauseBetweenCheckpoints(minPause);
            }

            if (config.hasPath(ConfigKeyName.FAIL_ON_CHECKPOINTING_ERRORS)) {
                boolean fail = config.getBoolean(ConfigKeyName.FAIL_ON_CHECKPOINTING_ERRORS);
                checkpointConfig.setFailOnCheckpointingErrors(fail);
            }
        }
    }


}
