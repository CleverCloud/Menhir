package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
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
