package base;

import groovy.lang.Writable;
import groovy.text.SimpleTemplateEngine;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.core.Response;
import org.codehaus.groovy.control.CompilationFailedException;
import util.MalformedTemplateException;
import util.Template;

/**
 *
 * @author keruspe
 */
@Stateless
@LocalBean
public class   Controller {
   
   public Response render(Map<String, Object> args) {
      StackTraceElement[] stes = new Throwable().getStackTrace();
      String potentialCaller = null;
      int index = 0;
      for (StackTraceElement ste : stes) {
         try {
            potentialCaller = ste.getClassName();
            if (! Controller.class.isAssignableFrom(Class.forName(potentialCaller)))
               break;
            ++index;
         } catch (ClassNotFoundException ex) {
            Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
      StackTraceElement ste = stes[index-1];
      String caller = ste.getClassName();
      String templateFile;
      try {
         templateFile = "/home/keruspe/Clever Cloud/Loose/src/main/java/app/views/" + Class.forName(caller).getSimpleName() + "/" + ste.getMethodName() + ".html";
      } catch (ClassNotFoundException ex) {
         Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
         templateFile = "/home/keruspe/Clever Cloud/Loose/src/main/java/app/views/404.html";
      }

      SimpleTemplateEngine engine = new SimpleTemplateEngine();
      Writable templated = null;
      Template template = new Template(templateFile);
      try {
         template.compute(args);
         templated = engine.createTemplate(template.toString()).make(args);
         //templated = engine.createTemplate(Controller.class.getClassLoader().getResource("../views/").getPath()).make(args);
      } catch (MalformedTemplateException ex) {
         Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
      } catch (CompilationFailedException ex) {
         Logger.getLogger(caller).log(Level.SEVERE, null, ex);
      } catch (ClassNotFoundException ex) {
         Logger.getLogger(caller).log(Level.SEVERE, null, ex);
      } catch (IOException ex) {
         Logger.getLogger(caller).log(Level.SEVERE, null, ex);
      }

      String response = (templated == null) ?  "" : templated.toString();
      return Response.ok(response).build();
   }
   
}
