/* Copyright 2019 The OpenTracing Authors
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

package jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import io.opentracing.contrib.specialagent.TestUtil;

public class App {
  public static void main(String[] args) throws Exception {
    Class.forName("org.h2.Driver");

    Connection conn = DriverManager.getConnection("jdbc:h2:mem:jdbc");
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery("show databases;");

    while (rs.next()) {
      System.out.println(rs.getString(1));
    }
    rs.close();
    stmt.close();
    conn.close();

    TestUtil.checkSpan("java-jdbc", 2);
  }
}