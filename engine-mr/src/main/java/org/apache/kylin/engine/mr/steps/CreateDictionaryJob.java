/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.apache.kylin.engine.mr.steps;

import java.io.IOException;

import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.util.ClassUtil;
import org.apache.kylin.common.util.Dictionary;
import org.apache.kylin.cube.cli.DictionaryGeneratorCLI;
import org.apache.kylin.dict.DictionaryProvider;
import org.apache.kylin.dict.DistinctColumnValuesProvider;
import org.apache.kylin.engine.mr.HadoopUtil;
import org.apache.kylin.engine.mr.SortedColumnDFSFile;
import org.apache.kylin.engine.mr.common.AbstractHadoopJob;
import org.apache.kylin.metadata.model.TblColRef;
import org.apache.kylin.source.ReadableTable;

public class CreateDictionaryJob extends AbstractHadoopJob {

    @Override
    public int run(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(OPTION_CUBE_NAME);
        options.addOption(OPTION_SEGMENT_ID);
        options.addOption(OPTION_INPUT_PATH);
        parseOptions(options, args);

        final String cubeName = getOptionValue(OPTION_CUBE_NAME);
        final String segmentID = getOptionValue(OPTION_SEGMENT_ID);
        final String factColumnsInputPath = getOptionValue(OPTION_INPUT_PATH);

        final KylinConfig config = KylinConfig.getInstanceFromEnv();

        DictionaryGeneratorCLI.processSegment(config, cubeName, segmentID, new DistinctColumnValuesProvider() {
            @Override
            public ReadableTable getDistinctValuesFor(TblColRef col) {
                return new SortedColumnDFSFile(factColumnsInputPath + "/" + col.getIdentity(), col.getType());
            }
        }, new DictionaryProvider() {

            @Override
            public Dictionary<String> getDictionary(TblColRef col) throws IOException {
                Path dictFile = new Path(factColumnsInputPath, col.getIdentity() + FactDistinctColumnsReducer.DICT_FILE_POSTFIX);
                FileSystem fs = HadoopUtil.getWorkingFileSystem();
                if (fs.exists(dictFile) == false)
                    return null;
                
                FSDataInputStream is = null;
                try {
                    is = fs.open(dictFile);
                    String dictClassName = is.readUTF();
                    Dictionary<String> dict = (Dictionary<String>) ClassUtil.newInstance(dictClassName);
                    dict.readFields(is);
                    logger.info("DictionaryProvider read dict from file: " + dictFile);
                    return dict;
                } finally {
                    IOUtils.closeQuietly(is);
                }
            }
        });

        return 0;
    }

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new CreateDictionaryJob(), args);
        System.exit(exitCode);
    }

}
