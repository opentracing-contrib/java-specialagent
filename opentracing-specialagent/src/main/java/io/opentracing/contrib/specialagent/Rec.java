package io.opentracing.contrib.specialagent;

import java.io.File;

public class Rec {
  private File file;

  private Rec(final File file) {
    this.file = file;
  }

  public boolean isDirectory() {
    return file.isDirectory();
  }


}
