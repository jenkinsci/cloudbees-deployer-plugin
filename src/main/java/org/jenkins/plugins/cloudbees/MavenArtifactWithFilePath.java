/*
 * Copyright 2010-2011, CloudBees Inc.
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
package org.jenkins.plugins.cloudbees;

import org.apache.commons.lang.StringUtils;

import java.io.Serializable;

/**
 * @author Olivier Lamy
 */
public class MavenArtifactWithFilePath
        implements Serializable {

    final String groupId, artifactId, version, filePath, type;

    MavenArtifactWithFilePath(String groupId, String artifactId, String version, String filePath, String type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.filePath = filePath;
        this.type = type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof MavenArtifactWithFilePath)) {
            return false;
        }

        MavenArtifactWithFilePath mavenArtifactWithFilePath = (MavenArtifactWithFilePath) obj;

        return StringUtils.equals(groupId, mavenArtifactWithFilePath.groupId)
                && StringUtils.equals(artifactId, mavenArtifactWithFilePath.artifactId)
                && StringUtils.equals(version, mavenArtifactWithFilePath.version)
                && StringUtils.equals(filePath, mavenArtifactWithFilePath.filePath) && StringUtils.equals(type,
                mavenArtifactWithFilePath.type);
    }

    @Override
    public int hashCode() {
        int hashCode = 37 + groupId == null ? 0 : groupId.hashCode();
        hashCode += artifactId == null ? 0 : artifactId.hashCode();
        hashCode += version == null ? 0 : version.hashCode();
        hashCode += filePath == null ? 0 : filePath.hashCode();
        hashCode += type == null ? 0 : type.hashCode();
        return hashCode;
    }

    @Override
    public String toString()
    {
        return "MavenArtifactWithFilePath{" + "groupId='" + groupId + '\'' + ", artifactId='" + artifactId + '\''
            + ", version='" + version + '\'' + ", filePath='" + filePath + '\'' + ", type='" + type + '\'' + '}';
    }
}
