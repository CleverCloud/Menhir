package app.models;

import base.Model;
import org.bson.types.ObjectId;

/**
 *
 * @author keruspe
 */
public class FooBar extends Model {
   
   private String foo;
   private String bar;
   
   public FooBar(String foo, String bar) {
      this.foo = foo;
      this.bar = bar;
   }
   
   public FooBar() {}

   public String getBar() {
      return this.bar;
   }

   public String getFoo() {
      return this.foo;
   }
   
   public static FooBar getById(ObjectId id) {
      return Model.getDs().find(FooBar.class, "_id", id).get();
   }
   
}
