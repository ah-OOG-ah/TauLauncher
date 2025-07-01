package org.taumc.launcher.core.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ProgressProvider {
    Task addTask(String taskName);

    interface Task extends AutoCloseable {
        void setProgress(float progress);
        void setMessage(String message);
        void close();
    }

    ProgressProvider NONE = new ProgressProvider() {
        @Override
        public Task addTask(String taskName) {
            return new Task() {
                @Override
                public void setProgress(float progress) {

                }

                @Override
                public void setMessage(String message) {

                }

                @Override
                public void close() {

                }
            };
        }
    };

    ProgressProvider LOGGING = new ProgressProvider() {
        private static final Logger LOGGER = LoggerFactory.getLogger(ProgressProvider.class);

        @Override
        public Task addTask(String taskName) {
            LOGGER.info("Start task '{}'", taskName);
            return new Task() {
                @Override
                public void setProgress(float progress) {
                    LOGGER.info("Task '{}' has progress {}%", taskName, String.format("%.1f", progress * 100));
                }

                @Override
                public void setMessage(String message) {

                }

                @Override
                public void close() {
                    LOGGER.info("Finish task '{}'", taskName);
                }
            };
        }
    };
}
