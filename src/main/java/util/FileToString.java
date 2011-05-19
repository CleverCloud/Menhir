package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: keruspe
 * Date: 18/05/11
 * Time: 11:29
 * To change this template use File | Settings | File Templates.
 */
public class FileToString {
   public String doJob(String path) throws IOException {
      StringBuilder sb = new StringBuilder();
      FileReader fr = new FileReader(path);
      BufferedReader br = new BufferedReader(fr);
      String line;
      while ((line = br.readLine()) != null) {
         sb.append(line).append("\n");
      }
      fr.close();
      return sb.toString();
   }
}
