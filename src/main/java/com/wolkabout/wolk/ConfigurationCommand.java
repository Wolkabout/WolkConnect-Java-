/*
 * Copyright (c) 2018 WolkAbout Technology s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.wolkabout.wolk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ConfigurationCommand {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationCommand.class);

    public enum CommandType {UNKNOWN, SET, CURRENT}

    private final CommandType commandType;

    private final Map<String, String> values;

    public ConfigurationCommand(CommandType commandType, Map<String, String> values) {
        this.commandType = commandType;
        this.values = values;
    }

    public CommandType getType() {
       return commandType;
    }

    public Map<String, String> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "ConfigurationCommand{" +
                "commandType='" + commandType + '\'' +
                ", values='" + values + '\'' +
                '}';
    }
}