/*
 * Copyright (C) 2019 Qunar, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package qunar.tc.bistoury.ui.git;

import com.google.common.base.Strings;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.GitlabAPIException;
import org.gitlab.api.http.Query;
import org.gitlab.api.models.GitlabProject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import qunar.tc.bistoury.serverside.bean.ApiResult;
import qunar.tc.bistoury.serverside.configuration.DynamicConfigLoader;
import qunar.tc.bistoury.serverside.configuration.local.LocalDynamicConfig;
import qunar.tc.bistoury.serverside.util.ResultHelper;
import qunar.tc.bistoury.ui.model.GitlabFile;

import javax.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;

/**
 * @author leix.xie
 * @date 2019/9/4 16:37
 * @describe
 */
@Component
public class GitlabRepositoryApiImpl implements GitRepositoryApi {

    @Autowired
    private GitlabApiCreateService gitlabApiCreateService;

    private String filePathFormat;

    @PostConstruct
    public void init() {
        DynamicConfigLoader.<LocalDynamicConfig>load("config.properties")
                .addListener(config -> filePathFormat = config.getString("file.path.format", "{0}src/main/java/{1}.java"));
    }

    @Override
    public ApiResult tree(String projectId, String path, String ref) {
        try {
            final GitlabAPI api = gitlabApiCreateService.create();
            final GitlabProject project = api.getProject(projectId);
            return ResultHelper.success(api.getRepositoryTree(project, path, ref));
        } catch (GitlabAPIException e) {
            return ResultHelper.fail(-1, "连接gitlab服务器失败，请核对private token", e);
        } catch (FileNotFoundException fnfe) {
            return ResultHelper.fail(-1, "文件不存在，请核对仓库地址", fnfe);
        } catch (IOException e) {
            return ResultHelper.fail(-1, "获取文件属失败", e);
        }
    }

    @Override
    public ApiResult file(String projectId, String path, String ref) throws IOException {
        return doFile(projectId, path, ref);
    }

    @Override
    public ApiResult fileByClass(String projectId, String ref, String module, String className) throws IOException {
        final String filePath = getFilePath(module, className);
        return doFile(projectId, ref, filePath);
    }

    private ApiResult doFile(final String projectId, final String ref, final String filepath) throws IOException {
        try {
            final GitlabAPI api = gitlabApiCreateService.create();
            final GitlabProject project = api.getProject(projectId);
            final Query query = new Query().append("file_path", filepath).append("ref", ref);
            final String url = "/projects/" + project.getId() + "/repository/files" + query.toString();
            return ResultHelper.success(api.retrieve().to(url, GitlabFile.class));
        } catch (GitlabAPIException e) {
            return ResultHelper.fail(-1, "连接gitlab服务器失败，请核private token", e);
        } catch (FileNotFoundException fnfe) {
            return ResultHelper.fail(-1, "文件不存在，请核对仓库地址", fnfe);
        }
    }

    private String getFilePath(String module, final String className) {
        if (".".equals(module) || Strings.isNullOrEmpty(module)) {
            module = "";
        } else {
            module = module + "/";
        }
        return MessageFormat.format(filePathFormat, module, className.replace(".", "/"));
    }
}
