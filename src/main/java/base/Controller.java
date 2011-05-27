package base;

import groovy.lang.Writable;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ws.rs.core.Response;

import util.Config;
import util.MalformedTemplateException;
import util.Template;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
@Stateless
@LocalBean
public class Controller {

   public Response render(Map<String, Object> args) {
      StackTraceElement[] stes = new Throwable().getStackTrace();
      String potentialCaller = null;
      int index = 0;
      for (StackTraceElement ste : stes) {
         try {
            potentialCaller = ste.getClassName();
            if (!Controller.class.isAssignableFrom(Class.forName(potentialCaller)))
               break;
            ++index;
         } catch (ClassNotFoundException ex) {
            Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
      StackTraceElement ste = stes[index - 1];
      String caller = ste.getClassName();
      String templateFile;
      try {
         templateFile = Config.PATH + Class.forName(caller).getSimpleName() + "/" + ste.getMethodName() + ".html";
      } catch (ClassNotFoundException ex) {
         Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
         templateFile = Config.PATH + "404.html";
      }

      String response = "";
      Template template;
      try {
         template = new Template(templateFile, null, null, null);
         response = template.compile(args);
      } catch (MalformedTemplateException ex) {
         Logger.getLogger(Controller.class.getName()).log(Level.SEVERE, null, ex);
         return Response.serverError().entity(ex.toString()).build();
      } catch (Exception ex) {
         Logger.getLogger(caller).log(Level.SEVERE, null, ex);
      }

      return Response.ok(response).build();
   }

}
