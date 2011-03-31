package org.jenkins.plugins.cloudbees;

import org.apache.commons.lang.StringUtils;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: olamy
 * Date: 31/03/11
 * Time: 21:49
 * To change this template use File | Settings | File Templates.
 */
public class MavenArtifactWithFilePath  implements Serializable {

    final String groupId,artifactId,version,filePath,type;

    MavenArtifactWithFilePath(String groupId, String artifactId, String version, String filePath,String type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.filePath = filePath;
        this.type = type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (!(obj instanceof MavenArtifactWithFilePath)) return false;

        MavenArtifactWithFilePath mavenArtifactWithFilePath = (MavenArtifactWithFilePath) obj;

        return StringUtils.equals(groupId, mavenArtifactWithFilePath.groupId)
                && StringUtils.equals(artifactId,mavenArtifactWithFilePath.artifactId)
                && StringUtils.equals(version,mavenArtifactWithFilePath.version)
                && StringUtils.equals(filePath,mavenArtifactWithFilePath.filePath)
                && StringUtils.equals(type,mavenArtifactWithFilePath.type);
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

}
