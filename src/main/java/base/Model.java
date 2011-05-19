package base;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.annotations.Transient;
import com.mongodb.Mongo;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.types.ObjectId;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
@SuppressWarnings("unchecked")
public abstract class Model {

   private static String host = "localhost";
   private static String db = "loose";

   @Id
   private ObjectId id;
   @Transient
   private static Datastore datastore;

   public ObjectId getId() {
      return this.id;
   }

   /**
    * Get the Datastore used to store the data
    *
    * @return The Datastore
    */
   public static Datastore getDs() {
      if (Model.datastore == null) {
         try {
            Mongo mongo = new Mongo(host);
            Model.datastore = (new Morphia()).createDatastore(mongo, db);
         } catch (Exception e) {
            Logger.getAnonymousLogger().log(Level.SEVERE, e.getMessage());
         }
      }

      return Model.datastore;
   }

   /**
    * Save the Entity
    *
    * @return self
    */
   public <T extends Model> T save() {
      Model.getDs().save(this);
      Model.getDs().ensureIndexes(this.getClass());
      return (T) Model.getDs().get(this);
   }

   public <T extends Model> T create() {
      Model.getDs().save(this);
      return (T) Model.getDs().get(this.getClass(), this.id);
   }

   /**
    * Refresh the Entity (fill missing field with values from BDD)
    *
    * @return self, refreshed
    */
   public <T extends Model> T refresh() {
      if (this.id == null) {
         return null;
      } else {
         T entity = (T) Model.getDs().get(this.getClass(), this.id);
         for (Field field : this.getClass().getFields()) {
            try {
               if (field.get(this) != null) {
                  field.set(entity, field.get(this));
               } else {
                  field.set(this, field.get(entity));
               }
            } catch (IllegalArgumentException ex) {
               Logger.getLogger(Model.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalAccessException ex) {
               Logger.getLogger(Model.class.getName()).log(Level.SEVERE, null, ex);
            }
         }

         return (T) this;
      }
   }

   /**
    * Delete the Entity
    */
   public void delete() {
      Model.getDs().delete(this);
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == null) {
         return false;
      }
      if (getClass() != obj.getClass()) {
         return false;
      }
      final Model other = (Model) obj;
      if (this.id != other.id && (this.id == null || !this.id.equals(other.id))) {
         return false;
      }
      return true;
   }

   @Override
   public int hashCode() {
      int hash = 5;
      hash = 89 * hash + (this.id != null ? this.id.hashCode() : 0);
      return hash;
   }
}
