package app.controllers;

import app.models.FooBar;
import app.models.HelloWorldDest;
import base.Controller;
import com.google.gson.Gson;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
@Path("/")
public class FooBarController extends Controller {

   @GET
   @Path("{id}")
   public Response getFooBar(@PathParam("id") String id) {
      FooBar fooBar = FooBar.getById(new ObjectId(id));
      if (fooBar == null)
         return Response.status(404).entity("Foobar " + id + " not found.").build();
      Map<String, Object> args = new HashMap<String, Object>();
      args.put("foobar", fooBar);
      args.put("hwd", new HelloWorldDest("World"));
      List<String> list = new ArrayList<String>();
      for (int i = 0; i < 4; ++i)
         list.add("ListItem" + i);
      args.put("list", list);
      return render(args);
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
