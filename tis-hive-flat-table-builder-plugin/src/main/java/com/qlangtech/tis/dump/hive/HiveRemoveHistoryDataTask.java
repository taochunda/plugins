/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 *   This program is free software: you can use, redistribute, and/or modify
 *   it under the terms of the GNU Affero General Public License, version 3
 *   or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *   FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.qlangtech.tis.dump.hive;

import com.qlangtech.tis.fs.ITISFileSystem;
import com.qlangtech.tis.fullbuild.indexbuild.IDumpTable;
import com.qlangtech.tis.order.dump.task.ITableDumpConstant;
import com.qlangtech.tis.sql.parser.tuple.creator.EntityName;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2015年11月26日 上午11:54:31
 */
public class HiveRemoveHistoryDataTask {

    private static final Logger logger = LoggerFactory.getLogger(HiveRemoveHistoryDataTask.class);


    // daily ps name
    private static final String pt = IDumpTable.PARTITION_PT;

    private final ITISFileSystem fileSystem;

    public static void main(String[] arg) {
//        List<PathInfo> timestampList = new ArrayList<PathInfo>();
//        PathInfo path = null;
//        for (int i = 0; i < 100; i++) {
//            path = new PathInfo();
//            path.setTimeStamp(i);
//            timestampList.add(path);
//        }
//        sortTimestamp(timestampList);
//        for (PathInfo info : timestampList) {
//            System.out.println(info.timeStamp);
//        }
    }

    public HiveRemoveHistoryDataTask(ITISFileSystem fsFactory) {
        super();
        this.fileSystem = fsFactory;
    }

    /**
     * @param hiveConnection
     * @throws Exception
     */
    public void deleteHdfsHistoryFile(EntityName dumpTable, Connection hiveConnection) {
        try {
            logger.info("start deleteHdfsHistoryFile data[{}] files", dumpTable);
            this.fileSystem.deleteHistoryFile(dumpTable);
            // this.deleteMetadata(dumpTable);
            // this.deleteHdfsFile(dumpTable, false);
            // 索引数据: /user/admin/search4totalpay/all/0/output/20160104003306
            // this.deleteHdfsFile(dumpTable, true);
            this.dropHistoryHiveTable(dumpTable, hiveConnection);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除特定timestamp下的文件，前一次用户已经导入了文件，后一次想立即重新导入一遍
     *
     * @param hiveConnection
     * @throws Exception
     */
    public void deleteHdfsHistoryFile(EntityName dumpTable, Connection hiveConnection, String timestamp) {
        try {
            logger.info("start delete history data{} files", dumpTable);
//            this.deleteMetadata(dumpTable, timestamp);
//            this.deleteHdfsFile(dumpTable, false, /* isBuildFile */
//                    timestamp);
//            // 索引数据: /user/admin/search4totalpay/all/0/output/20160104003306
//            this.deleteHdfsFile(dumpTable, true, /* isBuildFile */
//                    timestamp);
            this.fileSystem.deleteHistoryFile(dumpTable, timestamp);
            this.dropHistoryHiveTable(dumpTable, hiveConnection, (r) -> StringUtils.equals(r, timestamp), 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    /**
//     * 删除dump的metadata<br>
//     * example:/user/admin/search4customerregistercard/all/20160106131304
//     * example:/user/admin/scmdb/supply_goods/all/20160106131304
//     */
//    private void deleteMetadata(EntityName dumpTable) throws Exception {
//        this.deleteMetadata(dumpTable, (r) -> {
//            return true;
//        }, ITableDumpConstant.MAX_PARTITION_SAVE);
//    }

//    private void deleteMetadata(EntityName dumpTable, String timestamp) throws Exception {
//        if (StringUtils.isEmpty(timestamp)) {
//            throw new IllegalArgumentException("param timestamp can not be null");
//        }
//        this.deleteMetadata(dumpTable, (r) -> {
//                    return StringUtils.equals(r.getName(), timestamp);
//                }, // MAX_PARTITION_SAVE
//                0);
//    }

//    /**
//     * 删除dump的metadata<br>
//     * example:/user/admin/search4customerregistercard/all/20160106131304
//     * example:/user/admin/scmdb/supply_goods/all/20160106131304
//     */
//    private void deleteMetadata(EntityName dumpTable, ITISFileSystem.IPathFilter pathFilter, int maxPartitionSave) throws Exception {
//        String hdfsPath = getJoinTableStorePath(this.fileSystem.getRootDir(), dumpTable) + "/all";
//        logger.info("hdfsPath:{}", hdfsPath);
//        ITISFileSystem fileSys = this.fileSystem;
//        IPath parent = fileSys.getPath(hdfsPath);
//        // Path parent = new Path(hdfsPath);
//        if (!fileSys.exists(parent)) {
//            return;
//        }
//        List<IPathInfo> child = fileSys.listChildren(parent, pathFilter);
//        List<PathInfo> timestampList = new ArrayList<>();
//        PathInfo pathinfo;
//        Matcher matcher;
//        for (IPathInfo c : child) {
//            matcher = DATE_PATTERN.matcher(c.getPath().getName());
//            if (matcher.matches()) {
//                pathinfo = new PathInfo();
//                pathinfo.pathName = c.getPath().getName();
//                pathinfo.timeStamp = Long.parseLong(matcher.group());
//                timestampList.add(pathinfo);
//            }
//        }
//        if (timestampList.size() > 0) {
//            deleteOldHdfsfile(fileSys, parent, timestampList, maxPartitionSave);
//        }
//    }


//    /**
//     * 删除历史索引build文件
//     *
//     * @throws IOException
//     * @throws FileNotFoundException
//     */
//    public void removeHistoryBuildFile(EntityName dumpTable) throws IOException, FileNotFoundException {
//        this.deleteHdfsFile(dumpTable, true);
//    }

//    private void deleteHdfsFile(EntityName dumpTable, boolean isBuildFile) throws IOException {
//        this.deleteHdfsFile(dumpTable, isBuildFile, (r) -> true, ITableDumpConstant.MAX_PARTITION_SAVE);
//    }


//    /**
//     * 删除hdfs中的文件
//     *
//     * @param isBuildFile
//     * @throws IOException
//     */
//    private void deleteHdfsFile(EntityName dumpTable, boolean isBuildFile, ITISFileSystem.IPathFilter filter, int maxPartitionSave) throws IOException {
//        // dump数据: /user/admin/scmdb/supply_goods/all/0/20160105003307
//        String hdfsPath = getJoinTableStorePath(fileSystem.getRootDir(), dumpTable) + "/all";
//        ITISFileSystem fileSys = fileSystem;
//        int group = 0;
//        List<IPathInfo> children = null;
//        while (true) {
//            IPath parent = fileSys.getPath(hdfsPath + "/" + (group++));
//            if (isBuildFile) {
//                parent = fileSys.getPath(parent, "output");
//            }
//            if (!fileSys.exists(parent)) {
//                break;
//            }
//            children = fileSys.listChildren(parent, filter);
//            // FileStatus[] child = fileSys.listStatus(parent, filter);
//            List<PathInfo> dumpTimestamps = new ArrayList<>();
//            for (IPathInfo f : children) {
//                try {
//                    PathInfo pathinfo = new PathInfo();
//                    pathinfo.pathName = f.getPath().getName();
//                    pathinfo.timeStamp = Long.parseLong(f.getPath().getName());
//                    dumpTimestamps.add(pathinfo);
//                } catch (Throwable e) {
//                }
//            }
//            deleteOldHdfsfile(fileSys, parent, dumpTimestamps, maxPartitionSave);
//        }
//    }


    public void dropHistoryHiveTable(EntityName dumpTable, Connection conn) {
        this.dropHistoryHiveTable(dumpTable, conn, (r) -> true, ITableDumpConstant.MAX_PARTITION_SAVE);
    }

    /**
     * 删除hive中的历史表
     */
    public void dropHistoryHiveTable(EntityName dumpTable, Connection conn, PartitionFilter filter, int maxPartitionSave) {
        // String dbName = getDbName() == null ? "tis" : getDbName();
        // String tableName = getTableName();
        // String path = dbName + "." + tableName;
        // if (!(this.pathGetter instanceof DumpTable)) {
        // return;
        // }
        final EntityName table = dumpTable;
        if (StringUtils.isBlank(pt)) {
            throw new IllegalStateException("pt name shall be set");
        }
        try {
            // 判断表是否存在
            if (!HiveTableBuilder.isTableExists(conn, table)) {
                logger.info(table + " is not exist");
                return;
            }
            List<String> ptList = getHistoryPts(conn, filter, table);
            int count = 0;
            logger.info("maxPartitionSave:" + maxPartitionSave);
            for (int i = ptList.size() - 1; i >= 0; i--) {
                if ((++count) > maxPartitionSave) {
                    String alterSql = "alter table " + table + " drop partition (  " + pt + " = '" + ptList.get(i) + "' )";
                    try {
                        HiveDBUtils.execute(conn, alterSql);
                    } catch (Throwable e) {
                        logger.error("alterSql:" + alterSql, e);
                    }
                    logger.info("history table:" + table + ", partition:" + pt + "='" + ptList.get(i) + "', have been removed");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getHistoryPts(Connection conn, final IDumpTable table) throws Exception {
        return getHistoryPts(conn, (ps) -> true, table);
    }

    private static List<String> getHistoryPts(Connection conn, PartitionFilter filter, final IDumpTable table) throws Exception {
        final Set<String> ptSet = new HashSet<>();
        final String showPartition = "show partitions " + table.getFullName();
        final Pattern ptPattern = Pattern.compile(pt + "=(\\d+)");
        HiveDBUtils.query(conn, showPartition, result -> {
            Matcher matcher = ptPattern.matcher(result.getString(1));
            if (matcher.find() && filter.accept(matcher.group(1))) {
                ptSet.add(matcher.group(1));
            } else {
                logger.warn(table + ",partition" + result.getString(1) + ",is not match pattern:" + ptPattern);
            }
            return true;
        });
        List<String> ptList = new LinkedList<>(ptSet);
        Collections.sort(ptList);
        return ptList;
    }

    private interface PartitionFilter {

        boolean accept(String ps);
    }
}
