package app.controllers;

import app.models.FooBar;
import base.Controller;
import com.google.gson.Gson;
import javax.ejb.Stateless;
import javax.ejb.LocalBean;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import org.bson.types.ObjectId;

/**
 *
 * @author keruspe
 */
@Stateless
@LocalBean
@Path("/")
public class FooBarController extends Controller {

   @GET
   @Path("{id}")
   public Response getFooBar(@PathParam("id") String id) {
      FooBar fooBar = FooBar.getById(new ObjectId(id));
      if (fooBar == null)
         return Response.status(404).entity("Foobar " + id + " not found.").build();
      return render(fooBar);
   }
   
   @POST
   public Response newFooBar(String fooBarJSON) {
      FooBar fooBar = new Gson().fromJson(fooBarJSON, FooBar.class);
      if (fooBar == null)
         return Response.serverError().entity("Bad JSON").build();
      fooBar.save();
      return Response.ok("id: " + fooBar.getId().toStringMongod()).build();
   }
   
}
