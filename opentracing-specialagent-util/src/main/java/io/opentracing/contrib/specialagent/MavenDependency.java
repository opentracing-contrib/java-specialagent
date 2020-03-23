/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent;

public class MavenDependency {
  private String groupId;
  private String artifactId;
  private String version;
  private String classifier;
  private String type = "jar";

  public MavenDependency(final String groupId, final String artifactId, final String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public MavenDependency(final String groupId, final String artifactId, final String version, final String classifier, final String type) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.classifier = classifier;
    this.type = type;
  }

  public MavenDependency() {
  }

  public String getGroupId() {
    return this.groupId;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public String getArtifactId() {
    return this.artifactId;
  }

  public void setArtifactId(final String artifactId) {
    this.artifactId = artifactId;
  }

  public String getVersion() {
    return this.version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  public String getClassifier() {
    return this.classifier;
  }

  public void setClassifier(final String classifier) {
    this.classifier = classifier;
  }

  public String getType() {
    return this.type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  @Override
  public int hashCode() {
    int hashCode = 0;
    if (groupId != null)
      hashCode = hashCode * 37 + groupId.hashCode();

    if (artifactId != null)
      hashCode = hashCode * 37 + artifactId.hashCode();

    if (version != null)
      hashCode = hashCode * 37 + version.hashCode();

    if (classifier != null)
      hashCode = hashCode * 37 + classifier.hashCode();

    if (type != null)
      hashCode = hashCode * 37 + type.hashCode();

    return hashCode;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(getClass().isInstance(obj)))
      return false;

    final MavenDependency that = (MavenDependency)obj;
    if (getGroupId() != null ? !getGroupId().equals(that.getGroupId()) : that.getGroupId() != null)
      return false;

    if (getArtifactId() != null ? !getArtifactId().equals(that.getArtifactId()) : that.getArtifactId() != null)
      return false;

    if (getVersion() != null ? !getVersion().equals(that.getVersion()) : that.getVersion() != null)
      return false;

    if (getClassifier() != null ? !getClassifier().equals(that.getClassifier()) : that.getClassifier() != null)
      return false;

    if (getType() != null ? !getType().equals(that.getType()) : that.getType() != null)
      return false;

    return true;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(groupId).append(':');
    builder.append(artifactId).append(':');
    builder.append(type).append(':');
    if (classifier != null)
      builder.append(classifier).append(':');

    builder.append(version);
    return builder.toString();
  }
}