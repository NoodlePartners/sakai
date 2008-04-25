/**
 * $Id$
 * $URL$
 * DeveloperHelperService.java - entity-broker - Apr 13, 2008 5:42:38 PM - azeckoski
 **************************************************************************
 * Copyright (c) 2008 Centre for Applied Research in Educational Technologies, University of Cambridge
 * Licensed under the Educational Community License version 1.0
 * 
 * A copy of the Educational Community License has been included in this 
 * distribution and is available at: http://www.opensource.org/licenses/ecl1.php
 *
 * Aaron Zeckoski (azeckoski@gmail.com) (aaronz@vt.edu) (aaron@caret.cam.ac.uk)
 */

package org.sakaiproject.entitybroker;

import java.util.Locale;
import java.util.Set;

/**
 * Includes methods which are likely to be helpful to developers who are implementing
 * entity providers in Sakai and working with references
 * 
 * @author Aaron Zeckoski (aaron@caret.cam.ac.uk)
 */
public interface DeveloperHelperService {

   public static final String ADMIN_USER_ID = "admin";

   // USER

   /**
    * Get the user entity reference (e.g. /user/{userId} - not id, eid, or username) 
    * of the current user if there is one,
    * this is not equivalent to the current user id
    * 
    * @return the user entity reference (e.g. /user/{userId} - not id, eid, or username)
    */
   public String getCurrentUserReference();

   /**
    * Translate the userId into a user entity reference
    * 
    * @param userReference the user entity reference (e.g. /user/{userId} - not id, eid, or username)
    * @return the userId as extracted from this user entity reference
    */
   public String getUserIdFromRef(String userReference);

   /**
    * Translate the user entity reference into a userId
    * 
    * @param userId the internal user Id (needed from some Sakai API operations) (not the eid or username)
    * @return the user entity reference (e.g. /user/{userId})
    */
   public String getUserRefFromUserId(String userId);

   /**
    * @return the Locale for the current user or the system set locale
    */
   public Locale getCurrentLocale();

   // LOCATION

   /**
    * @return the entity reference of the current location for the current session
    * (represents the current site/group of the current user in the system)
    */
   public String getCurrentLocationReference();

   /**
    * @return the entity reference of the current active tool for the current session
    * (represents the tool that is currently being used by the current user in the system)
    */
   public String getCurrentToolReference();

   /**
    * Translate a tool entity reference into a tool Id 
    * 
    * @param toolReference the entity reference of a tool (e.g. /tool/{toolId})
    * @return the toolId (needed for other Sakai API operations)
    */
   public String getToolIdFromToolRef(String toolReference);

   // PERMISSIONS

   /**
    * Check if this user has super admin level access (permissions)
    * 
    * @param userReference the user entity reference (e.g. /user/{userId} - not id, eid, or username)
    * @return true if the user has admin access, false otherwise
    */
   public boolean isUserAdmin(String userReference);

   /**
    * Check if a user has a specified permission for the entity reference, 
    * primarily a convenience method for checking location permissions
    * 
    * @param userReference the user entity reference (e.g. /user/{userId} - not id, eid, or username)
    * @param permission a permission string constant
    * @param reference a globally unique reference to an entity, 
    * consists of the entity prefix and optional segments (normally the id at least)
    * @return true if allowed, false otherwise
    */
   public boolean isUserAllowedInEntityReference(String userReference, String permission, String reference);

   /**
    * Find the entity references which a user has a specific permission in,
    * this is most commonly used to get the list of sites which a user has a permission in but
    * it will work for any entity type which uses Sakai permissions
    * 
    * @param userReference the user entity reference (e.g. /user/{userId} - not id, eid, or username)
    * @param permission a permission string constant
    * @return a set of entity references - a globally unique reference to an entity, 
    * consists of the entity prefix and optional segments (normally the id at least)
    */
   public Set<String> getEntityReferencesForUserAndPermission(String userReference, String permission);

   /**
    * Get the user references which have the given permission in the given entity reference,
    * this is most commonly used to get the users which have a permission in a site but it should
    * work for any entity type which uses Sakai permissions
    * 
    * @param reference a globally unique reference to an entity, 
    * consists of the entity prefix and optional segments (normally the id at least)
    * @param permission a permission string constant
    * @return a set of user entity references (e.g. /user/{userId} - not id, eid, or username)
    */
   public Set<String> getUserReferencesForEntityReference(String reference, String permission);

   // BEANS

   /**
    * Deep clone a bean (object) and all the values in it into a brand new object of the same type,
    * this will traverse the bean and will make new objects for all non-null values contained in the object,
    * the level indicates the number of contained objects to traverse and clone,
    * setting this to zero will only clone basic type values in the bean,
    * setting this to one will clone basic fields, references, and collections in the bean,
    * etc.<br/>
    * This is mostly useful for making a copy of a hibernate object so it will no longer 
    * be the persistent object with the hibernate proxies and lazy loading
    * 
    * @param <T>
    * @param bean any java bean, this can also be a list, map, array, or any simple
    * object, it does not have to be a custom object or even a java bean,
    * also works with apache beanutils DynaBeans
    * @param maxDepth the number of objects to follow when traveling through the object and copying
    * the values from it, 0 means to only copy the simple values in the object, any objects will
    * be ignored and will end up as nulls, 1 means to follow the first objects found and copy all
    * of their simple values as well, and so forth
    * @param propertiesToSkip the names of properties to skip while cloning this object,
    * this only has an effect on the bottom level of the object, any properties found
    * on child objects will always be copied (if the maxDepth allows)
    * @return the clone of the bean
    * @throws IllegalArgumentException if there is a failure cloning the bean
    */
   public <T> T cloneBean(T bean, int maxDepth, String[] propertiesToSkip);

   /**
    * Deep copies one bean (object) into another, this is primarily for copying between identical types of objects but
    * it can also handle copying between objects which are quite different, 
    * this does not just do a reference copy of the values but actually creates new objects in the current classloader
    * and traverses through all properties of the object to make a complete deep copy
    * 
    * @param original the original object to copy from
    * @param destination the object to copy the values to (must have the same fields with the same types)
    * @param maxDepth the number of objects to follow when traveling through the object and copying
    * the values from it, 0 means to only copy the simple values in the object, any objects will
    * be ignored and will end up as nulls, 1 means to follow the first objects found and copy all
    * of their simple values as well, and so forth
    * @param fieldNamesToSkip the names of fields to skip while cloning this object,
    * this only has an effect on the bottom level of the object, any fields found
    * on child objects will always be copied (if the maxDepth allows)
    * @param ignoreNulls if true then nulls are not copied and the destination retains the value it has,
    * if false then nulls are copied and the destination value will become a null if the original value is a null
    * @throws IllegalArgumentException if the copy cannot be completed because the objects to copy do not have matching fields or types
    */
   public void copyBean(Object orig, Object dest, int maxDepth, String[] fieldNamesToSkip, boolean ignoreNulls);

}
