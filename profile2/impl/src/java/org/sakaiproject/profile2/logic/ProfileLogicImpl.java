package org.sakaiproject.profile2.logic;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.sakaiproject.profile2.model.Message;
import org.sakaiproject.profile2.model.MessageThread;
import org.sakaiproject.profile2.model.Person;
import org.sakaiproject.profile2.model.ProfileFriend;
import org.sakaiproject.profile2.model.ProfileImage;
import org.sakaiproject.profile2.model.ProfileImageExternal;
import org.sakaiproject.profile2.model.ProfilePreferences;
import org.sakaiproject.profile2.model.ProfilePrivacy;
import org.sakaiproject.profile2.model.ProfileStatus;
import org.sakaiproject.profile2.model.ResourceWrapper;
import org.sakaiproject.profile2.model.SearchResult;
import org.sakaiproject.profile2.util.ProfileConstants;
import org.sakaiproject.profile2.util.ProfileUtils;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

import twitter4j.Twitter;

/**
 * This is an internal Profile2 API Implementation to be used by the Profile2 tool only. 
 * 
 * <p>DO NOT USE THIS YOURSELF, use the ProfileServices instead.</p>
 * 
 * <p>If there are methods here that do not have an appropriate exposure in the service APIs, please file a JIRA at <a href="http://jira.sakaiproject.org/browse/PRFL">http://jira.sakaiproject.org/browse/PRFL</a></p>
 * 
 * @author Steve Swinsburg (s.swinsburg@lancaster.ac.uk)
 *
 */
public class ProfileLogicImpl extends HibernateDaoSupport implements ProfileLogic {

	private static final Logger log = Logger.getLogger(ProfileLogicImpl.class);

	// Hibernate query constants
	private static final String QUERY_GET_FRIEND_REQUESTS_FOR_USER = "getFriendRequestsForUser"; 
	private static final String QUERY_GET_CONFIRMED_FRIEND_USERIDS_FOR_USER = "getConfirmedFriendUserIdsForUser"; 
	private static final String QUERY_GET_FRIEND_REQUEST = "getFriendRequest"; 
	private static final String QUERY_GET_FRIEND_RECORD = "getFriendRecord"; 
	private static final String QUERY_GET_USER_STATUS = "getUserStatus"; 
	private static final String QUERY_GET_PRIVACY_RECORD = "getPrivacyRecord"; 
	private static final String QUERY_GET_CURRENT_PROFILE_IMAGE_RECORD = "getCurrentProfileImageRecord"; 
	private static final String QUERY_OTHER_PROFILE_IMAGE_RECORDS = "getOtherProfileImageRecords"; 
	private static final String QUERY_FIND_SAKAI_PERSONS_BY_NAME_OR_EMAIL = "findSakaiPersonsByNameOrEmail"; 
	private static final String QUERY_FIND_SAKAI_PERSONS_BY_INTEREST = "findSakaiPersonsByInterest"; 
	private static final String QUERY_LIST_ALL_SAKAI_PERSONS = "listAllSakaiPersons"; 
	private static final String QUERY_GET_PREFERENCES_RECORD = "getPreferencesRecord"; 
	private static final String QUERY_GET_EXTERNAL_IMAGE_RECORD = "getProfileImageExternalRecord"; 
	private static final String QUERY_GET_ALL_UNREAD_MESSAGES_COUNT = "getAllUnreadMessagesCount";
	private static final String QUERY_GET_THREADS_WITH_UNREAD_MESSAGES_COUNT = "getThreadsWithUnreadMessagesCount";
	private static final String QUERY_GET_LATEST_MESSAGE_IN_THREAD = "getLatestMessageInThread";
	private static final String QUERY_GET_MESSAGE_THREADS="getMessageThreads";
	private static final String QUERY_GET_MESSAGE_THREADS_COUNT="getMessageThreadsCount";
	private static final String QUERY_GET_MESSAGES_IN_THREAD="getMessagesInThread";
	private static final String QUERY_GET_MESSAGES_IN_THREAD_COUNT="getMessagesInThreadCount";
	private static final String QUERY_GET_MESSAGE="getMessage";
	private static final String QUERY_GET_THREAD="getThread";


	// Hibernate object fields
	private static final String USER_UUID = "userUuid";
	private static final String FRIEND_UUID = "friendUuid";
	private static final String CONFIRMED = "confirmed";
	private static final String OLDEST_STATUS_DATE = "oldestStatusDate";
	private static final String SEARCH = "search";
	private static final String TO = "to";
	private static final String THREAD = "thread";
	private static final String ID = "id";



	
	/**
 	 * {@inheritDoc}
 	 */
	public List<Person> getConnectionsForUser(String userId) {
		
		List<User> users = new ArrayList<User>();
		List<Person> connections = new ArrayList<Person>();
		users = UserDirectoryService.getUsers(getConfirmedConnectionUserIdsForUser(userId));
		
		for(User u: users) {
			Person p = new Person();
			p.setUuid(u.getId());
			p.setDisplayName(u.getDisplayName());
			p.setType(u.getType());
				
			connections.add(p);
		}
		
		return connections;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */	
	public int getCountConnectionsForUser(final String userId) {
		return getConnectionsForUser(userId).size();
	}
	
	/**
 	 * {@inheritDoc}
 	 */	
	public List<Person> getConnectionRequestsForUser(final String userId) {
		
		List<User> users = new ArrayList<User>();
		List<Person> requests = new ArrayList<Person>();
		users = UserDirectoryService.getUsers(getRequestedConnectionUserIdsForUser(userId));
		
		for(User u: users) {
			Person p = new Person();
			p.setUuid(u.getId());
			p.setDisplayName(u.getDisplayName());
			p.setType(u.getType());
				
			requests.add(p);
		}
		
		return requests;
	}

	
	/**
 	 * {@inheritDoc}
 	 */
	public List<Person> getConnectionsSubsetForSearch(List<Person> connections, String search) {
		
		List<Person> subList = new ArrayList<Person>();
		
		for(Person p : connections){
			if(StringUtils.startsWithIgnoreCase(p.getDisplayName(), search)) {
				if(subList.size() == ProfileConstants.MAX_CONNECTIONS_PER_SEARCH) {
					break;
				}
				subList.add(p);
			}
		}
		return subList;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean requestFriend(String userId, String friendId) {
		if(userId == null || friendId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.getFriendsForUser"); 
	  	}
		
		//check values are valid, ie userId, friendId etc
		
		try {
			//make a ProfileFriend object with 'Friend Request' constructor
			ProfileFriend profileFriend = new ProfileFriend(userId, friendId, ProfileConstants.RELATIONSHIP_FRIEND);
			getHibernateTemplate().save(profileFriend);
			log.info("User: " + userId + " requested friend: " + friendId);  
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.requestFriend() failed. " + e.getClass() + ": " + e.getMessage());  
			return false;
		}
	
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isFriendRequestPending(String fromUser, String toUser) {
		
		ProfileFriend profileFriend = getPendingFriendRequest(fromUser, toUser);

		if(profileFriend == null) {
			log.debug("ProfileLogic.isFriendRequestPending: No pending friend request from userId: " + fromUser + " to friendId: " + toUser + " found.");   
			return false;
		}
		
		Person person = new Person();
		return true;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean confirmFriendRequest(final String fromUser, final String toUser) {
		
		if(fromUser == null || toUser == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.confirmFriendRequest"); 
	  	}
		
		//get pending ProfileFriend object request for the given details
		ProfileFriend profileFriend = getPendingFriendRequest(fromUser, toUser);

		if(profileFriend == null) {
			log.error("ProfileLogic.confirmFriendRequest() failed. No pending friend request from userId: " + fromUser + " to friendId: " + toUser + " found.");   
			return false;
		}
		
	  	//make necessary changes to the ProfileFriend object.
	  	profileFriend.setConfirmed(true);
	  	profileFriend.setConfirmedDate(new Date());
		
		//save
		try {
			getHibernateTemplate().update(profileFriend);
			log.info("User: " + fromUser + " confirmed friend request from: " + toUser);  
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.confirmFriendRequest() failed. " + e.getClass() + ": " + e.getMessage());  
			return false;
		}
	
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean ignoreFriendRequest(final String fromUser, final String toUser) {
		
		if(fromUser == null || toUser == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.ignoreFriendRequest"); 
	  	}
		
		//get pending ProfileFriend object request for the given details
		ProfileFriend profileFriend = getPendingFriendRequest(fromUser, toUser);

		if(profileFriend == null) {
			log.error("ProfileLogic.ignoreFriendRequest() failed. No pending friend request from userId: " + fromUser + " to friendId: " + toUser + " found.");   
			return false;
		}
		
	  	
		//delete
		try {
			getHibernateTemplate().delete(profileFriend);
			log.info("User: " + toUser + " ignored friend request from: " + fromUser);  
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.ignoreFriendRequest() failed. " + e.getClass() + ": " + e.getMessage());  
			return false;
		}
	
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean removeFriend(String userId, String friendId) {
		
		if(userId == null || friendId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.removeFriend"); 
	  	}
		
		//get the friend object for this connection pair (could be any way around)
		ProfileFriend profileFriend = getFriendRecord(userId, friendId);
		
		if(profileFriend == null){
			log.error("ProfileFriend record does not exist for userId: " + userId + ", friendId: " + friendId);  
			return false;
		}
				
		//if ok, delete it
		try {
			getHibernateTemplate().delete(profileFriend);
			log.info("User: " + userId + " removed friend: " + friendId);  
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.removeFriend() failed. " + e.getClass() + ": " + e.getMessage());  
			return false;
		}
		
		
	}
	
	//only gets a pending request
	private ProfileFriend getPendingFriendRequest(final String userId, final String friendId) {
		
		if(userId == null || friendId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.getPendingFriendRequest"); 
	  	}
		
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_FRIEND_REQUEST);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setParameter(FRIEND_UUID, friendId, Hibernate.STRING);
	  			q.setParameter(CONFIRMED, false, Hibernate.BOOLEAN);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		return (ProfileFriend) getHibernateTemplate().execute(hcb);
	
	}

	
	/**
 	 * {@inheritDoc}
 	 */
	public ProfileStatus getUserStatus(final String userId) {
		
		if(userId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.getUserStatus"); 
	  	}
		
		// compute oldest date for status 
		Calendar cal = Calendar.getInstance(); 
		cal.add(Calendar.DAY_OF_YEAR, -7); 
		final Date oldestStatusDate = cal.getTime(); 
				
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_USER_STATUS);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setParameter(OLDEST_STATUS_DATE, oldestStatusDate, Hibernate.DATE);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		return (ProfileStatus) getHibernateTemplate().execute(hcb);
	}
	
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean setUserStatus(String userId, String status) {
		
		//create object
		ProfileStatus profileStatus = new ProfileStatus(userId,status,new Date());
		
		return setUserStatus(profileStatus);
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean setUserStatus(ProfileStatus profileStatus) {
		
		try {
			//only allowing oen status object per user, hence saveOrUpdate
			getHibernateTemplate().saveOrUpdate(profileStatus);
			log.info("Updated status for user: " + profileStatus.getUserUuid()); 
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.setUserStatus() failed. " + e.getClass() + ": " + e.getMessage()); 
			return false;
		}
		
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean clearUserStatus(String userId) {
		
		//validate userId here - TODO
		
		ProfileStatus profileStatus = getUserStatus(userId);
		
		if(profileStatus == null){
			log.error("ProfileStatus null for userId: " + userId); 
			return false;
		}
				
		//if ok, delete it
		try {
			getHibernateTemplate().delete(profileStatus);
			log.info("User: " + userId + " cleared status");  
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.clearUserStatus() failed. " + e.getClass() + ": " + e.getMessage());  
			return false;
		}
		
	}

	
	

	
	
	/**
 	 * {@inheritDoc}
 	 */
	public ProfilePrivacy createDefaultPrivacyRecord(String userId) {
		
		//see ProfilePrivacy for this constructor and what it all means
		ProfilePrivacy profilePrivacy = getDefaultPrivacyRecord(userId);
		
		//save
		try {
			getHibernateTemplate().save(profilePrivacy);
			log.info("Created default privacy record for user: " + userId); 
			return profilePrivacy;
		} catch (Exception e) {
			log.error("ProfileLogic.createDefaultPrivacyRecord() failed. " + e.getClass() + ": " + e.getMessage());  
			return null;
		}
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public ProfilePrivacy getDefaultPrivacyRecord(String userId) {
		
		//get the overriden privacy settings. they'll be defaults if not specified
		HashMap<String, Object> props = sakaiProxy.getOverriddenPrivacySettings();	
		
		//using the props, set them into the ProfilePrivacy object
		ProfilePrivacy profilePrivacy = new ProfilePrivacy(
				userId,
				(Integer)props.get("profileImage"),
				(Integer)props.get("basicInfo"),
				(Integer)props.get("contactInfo"),
				(Integer)props.get("academicInfo"),
				(Integer)props.get("personalInfo"),
				(Boolean)props.get("birthYear"),
				(Integer)props.get("search"),
				(Integer)props.get("myFriends"),
				(Integer)props.get("myStatus")
		);
		
		return profilePrivacy;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public ProfilePrivacy getPrivacyRecordForUser(final String userId) {
		
		if(userId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.getPrivacyRecordForUser"); 
	  	}
		
		ProfilePrivacy privacy = null;
		
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_PRIVACY_RECORD);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		privacy = (ProfilePrivacy) getHibernateTemplate().execute(hcb);
		
		//if none, get a default, which can be overridden by sakai.properties
		if(privacy == null) {
			privacy = this.getDefaultPrivacyRecord(userId);
		}
		
		return privacy;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean savePrivacyRecord(ProfilePrivacy profilePrivacy) {

		//if changes not allowed
		if(!sakaiProxy.isPrivacyChangeAllowedGlobally()) {
			log.warn("Privacy changes are not permitted as per sakai.properties setting 'profile2.privacy.change.enabled'.");
			return false;
		}
		
		try {
			getHibernateTemplate().saveOrUpdate(profilePrivacy);
			log.info("Saved privacy record for user: " + profilePrivacy.getUserUuid()); 
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.savePrivacyRecordForUser() failed. " + e.getClass() + ": " + e.getMessage());  
			return false;
		}
		
	}
	

	/**
 	 * {@inheritDoc}
 	 */
	public boolean addNewProfileImage(final String userId, final String mainResource, final String thumbnailResource) {
		
		Boolean success = (Boolean) getHibernateTemplate().execute(new HibernateCallback() {			
				public Object doInHibernate(Session session) throws HibernateException, SQLException {
					try {
						//first get the current ProfileImage records for this user
						List<ProfileImage> currentImages = new ArrayList<ProfileImage>(getCurrentProfileImageRecords(userId));
            
						for(Iterator<ProfileImage> i = currentImages.iterator(); i.hasNext();){
							ProfileImage currentImage = (ProfileImage)i.next();
              
							//invalidate each
							currentImage.setCurrent(false);
              
							//save
							session.update(currentImage);
						}
						//now create a new ProfileImage object with the new data - this is our new current ProfileImage
						ProfileImage newProfileImage = new ProfileImage(userId, mainResource, thumbnailResource, true);
              
						//save the new ProfileImage to the db
						session.save(newProfileImage);
						
						// flush session
			            session.flush();
            
					} catch(Exception e) {
						log.error("ProfileLogic.saveProfileImageRecord() failed. " + e.getClass() + ": " + e.getMessage()); 
						return Boolean.FALSE;
					}
					return Boolean.TRUE;
				}			
		});
		return success.booleanValue();
	}


	/**
 	 * {@inheritDoc}
 	 */
	public List<SearchResult> findUsersByNameOrEmail(String search, String userId) {
		
		//perform search (uses private method to wrap the two searches into one)
		List<String> userUuids = new ArrayList<String>(findUsersByNameOrEmail(search));

		//restrict to only return the max number. UI will print message
		int maxResults = ProfileConstants.MAX_SEARCH_RESULTS;
		if(userUuids.size() >= maxResults) {
			userUuids = userUuids.subList(0, maxResults);
		}
		
		//format into SearchResult records (based on friend status, privacy status etc)
		List<SearchResult> results = new ArrayList<SearchResult>(createSearchResultRecordsFromSearch(userUuids, userId));
		
		return results;
		
	}
	
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public List<SearchResult> findUsersByInterest(String search, String userId) {
		
		//perform search (uses private method to wrap the search)
		List<String> userUuids = new ArrayList<String>(findSakaiPersonsByInterest(search));
		
		//restrict to only return the max number. UI will print message
		int maxResults = ProfileConstants.MAX_SEARCH_RESULTS;
		if(userUuids.size() >= maxResults) {
			userUuids = userUuids.subList(0, maxResults);
		}
		
		//format into SearchResult records (based on friend status, privacy status etc)
		List<SearchResult> results = new ArrayList<SearchResult>(createSearchResultRecordsFromSearch(userUuids, userId));
		
		return results;
		
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public List<String> listAllSakaiPersons() {
		
		List<String> userUuids = new ArrayList<String>();
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  			Query q = session.getNamedQuery(QUERY_LIST_ALL_SAKAI_PERSONS);
	  			return q.list();
	  		}
	  	};
	  	
	  	userUuids = (List<String>) getHibernateTemplate().executeFind(hcb);
	
	  	return userUuids;
	}
	
	
	
	
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXFriendOfUserY(String userX, String userY) {
		
		//if same then friends.
		//added this check so we don't need to do it everywhere else and can call isFriend for all user pairs.
		if(userY.equals(userX)) {
			return true;
		}
		
		//get friends of current user
		//TODO change this to be a single lookup rather than iterating over a list
		List<String> friendUuids = new ArrayList<String>(getConfirmedConnectionUserIdsForUser(userY));
		
		//if list of confirmed friends contains this user, they are a friend
		if(friendUuids.contains(userX)) {
			return true;
		}
		
		return false;
	}
	
		
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXVisibleInSearchesByUserY(String userX, String userY, boolean friend) {
				
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//get privacy record for userX
    	ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userX);
    	
    	//pass to main
    	return isUserXVisibleInSearchesByUserY(userX, profilePrivacy, userY, friend);
		
	}
	
	

	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXVisibleInSearchesByUserY(String userX, ProfilePrivacy profilePrivacy, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//if no privacy record, return whatever the flag is set as by default
    	/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.SELF_SEARCH_VISIBILITY;
    	}
    	*/
    	
    	//if restricted to only self, not allowed
    	/* DEPRECATED via PRFL-24 when the privacy settings were relaxed
    	if(profilePrivacy.getProfile() == ProfilePrivacyManager.PRIVACY_OPTION_ONLYME) {
    		return false;
    	}
    	*/
		
    	//if friend and set to friends only
    	if(friend && profilePrivacy.getSearch() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return true;
    	}
    	
    	//if not friend and set to friends only
    	if(!friend && profilePrivacy.getSearch() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return false;
    	}
    	
    	//if everyone is allowed
    	if(profilePrivacy.getSearch() == ProfileConstants.PRIVACY_OPTION_EVERYONE) {
    		return true;
    	}
    	
    	//uncaught rule, return false
    	log.error("ProfileLogic.isUserXVisibleInSearchesByUserY. Uncaught rule. userX: " + userX + ", userY: " + userY + ", friend: " + friend);   
    	return false;
	}
	
	
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXProfileImageVisibleByUserY(String userX, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//get privacy record for userX
    	ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userX);
    	
    	//pass to main
    	return isUserXProfileImageVisibleByUserY(userX, profilePrivacy, userY, friend);
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXProfileImageVisibleByUserY(String userX, ProfilePrivacy profilePrivacy, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//if no privacy record, return whatever the flag is set as by default
    	/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.DEFAULT_PROFILEIMAGE_VISIBILITY;
    	}
    	*/
    	
    	//if restricted to only self, not allowed
    	/* DEPRECATED via PRFL-24 when the privacy settings were relaxed
    	if(profilePrivacy.getProfile() == ProfilePrivacyManager.PRIVACY_OPTION_ONLYME) {
    		return false;
    	}
    	*/
    	
    	//if user is friend and friends are allowed
    	if(friend && profilePrivacy.getProfileImage() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return true;
    	}
    	
    	//if not friend and set to friends only
    	if(!friend && profilePrivacy.getProfileImage() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return false;
    	}
    	
    	//if everyone is allowed
    	if(profilePrivacy.getProfileImage() == ProfileConstants.PRIVACY_OPTION_EVERYONE) {
    		return true;
    	}
    	
    	//uncaught rule, return false
    	log.error("ProfileLogic.isUserProfileImageVisibleByCurrentUser. Uncaught rule. userX: " + userX + ", userY: " + userY + ", friend: " + friend);   
    	return false;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXBasicInfoVisibleByUserY(String userX, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//get privacy record for userX
    	ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userX);
    	
    	//pass to main
    	return isUserXBasicInfoVisibleByUserY(userX, profilePrivacy, userY, friend);
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXBasicInfoVisibleByUserY(String userX, ProfilePrivacy profilePrivacy, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//if no privacy record, return whatever the flag is set as by default
    	/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.DEFAULT_BASICINFO_VISIBILITY;
    	}
    	*/
    	
    	//if restricted to only self, not allowed
    	if(profilePrivacy.getBasicInfo() == ProfileConstants.PRIVACY_OPTION_ONLYME) {
    		return false;
    	}
    	
    	//if user is friend and friends are allowed
    	if(friend && profilePrivacy.getBasicInfo() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return true;
    	}
    	
    	//if not friend and set to friends only
    	if(!friend && profilePrivacy.getBasicInfo() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return false;
    	}
    	
    	//if everyone is allowed
    	if(profilePrivacy.getBasicInfo() == ProfileConstants.PRIVACY_OPTION_EVERYONE) {
    		return true;
    	}
    	
    	//uncaught rule, return false
    	log.error("ProfileLogic.isUserXBasicInfoVisibleByUserY. Uncaught rule. userX: " + userX + ", userY: " + userY + ", friend: " + friend);   
    	return false;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXContactInfoVisibleByUserY(String userX, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//get privacy record for this user
    	ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userX);
    	
    	//pass to main
    	return isUserXContactInfoVisibleByUserY(userX, profilePrivacy, userY, friend);
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXContactInfoVisibleByUserY(String userX, ProfilePrivacy profilePrivacy, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//if no privacy record, return whatever the flag is set as by default
    	/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.DEFAULT_CONTACTINFO_VISIBILITY;
    	}
    	*/
    	
    	//if restricted to only self, not allowed
    	if(profilePrivacy.getContactInfo() == ProfileConstants.PRIVACY_OPTION_ONLYME) {
    		return false;
    	}
    	
    	//if user is friend and friends are allowed
    	if(friend && profilePrivacy.getContactInfo() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return true;
    	}
    	
    	//if not friend and set to friends only
    	if(!friend && profilePrivacy.getContactInfo() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return false;
    	}
    	
    	//if everyone is allowed
    	if(profilePrivacy.getContactInfo() == ProfileConstants.PRIVACY_OPTION_EVERYONE) {
    		return true;
    	}
    	
    	//uncaught rule, return false
    	log.error("ProfileLogic.isUserXContactInfoVisibleByUserY. Uncaught rule. userX: " + userX + ", userY: " + userY + ", friend: " + friend);   

    	return false;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXAcademicInfoVisibleByUserY(String userX, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//get privacy record for this user
    	ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userX);
    	
    	//pass to main
    	return isUserXAcademicInfoVisibleByUserY(userX, profilePrivacy, userY, friend);
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXAcademicInfoVisibleByUserY(String userX, ProfilePrivacy profilePrivacy, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//if no privacy record, return whatever the flag is set as by default
    	/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.DEFAULT_ACADEMICINFO_VISIBILITY;
    	}
    	*/
    	
    	//if restricted to only self, not allowed
    	if(profilePrivacy.getAcademicInfo() == ProfileConstants.PRIVACY_OPTION_ONLYME) {
    		return false;
    	}
    	
    	//if user is friend and friends are allowed
    	if(friend && profilePrivacy.getAcademicInfo() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return true;
    	}
    	
    	//if not friend and set to friends only
    	if(!friend && profilePrivacy.getAcademicInfo() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return false;
    	}
    	
    	//if everyone is allowed
    	if(profilePrivacy.getAcademicInfo() == ProfileConstants.PRIVACY_OPTION_EVERYONE) {
    		return true;
    	}
    	
    	//uncaught rule, return false
    	log.error("ProfileLogic.isUserXAcademicInfoVisibleByUserY. Uncaught rule. userX: " + userX + ", userY: " + userY + ", friend: " + friend);   

    	return false;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXPersonalInfoVisibleByUserY(String userX, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//get privacy record for this user
    	ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userX);
    	
    	//pass to main
    	return isUserXPersonalInfoVisibleByUserY(userX, profilePrivacy, userY, friend);
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXPersonalInfoVisibleByUserY(String userX, ProfilePrivacy profilePrivacy, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//if no privacy record, return whatever the flag is set as by default
    	/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.DEFAULT_PERSONALINFO_VISIBILITY;
    	}
    	*/
    	
    	//if restricted to only self, not allowed
    	if(profilePrivacy.getPersonalInfo() == ProfileConstants.PRIVACY_OPTION_ONLYME) {
    		return false;
    	}
    	
    	//if user is friend and friends are allowed
    	if(friend && profilePrivacy.getPersonalInfo() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return true;
    	}
    	
    	//if not friend and set to friends only
    	if(!friend && profilePrivacy.getPersonalInfo() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return false;
    	}
    	
    	//if everyone is allowed
    	if(profilePrivacy.getPersonalInfo() == ProfileConstants.PRIVACY_OPTION_EVERYONE) {
    		return true;
    	}
    	    	
    	//uncaught rule, return false
    	log.error("ProfileLogic.isUserXPersonalInfoVisibleByUserY. Uncaught rule. userX: " + userX + ", userY: " + userY + ", friend: " + friend);   
    	return false;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXFriendsListVisibleByUserY(String userX, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//get privacy record for this user
    	ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userX);
    	
    	//pass to main
    	return isUserXFriendsListVisibleByUserY(userX, profilePrivacy, userY, friend);
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXFriendsListVisibleByUserY(String userX, ProfilePrivacy profilePrivacy, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
	
		//if no privacy record, return whatever the flag is set as by default
    	/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.DEFAULT_MYFRIENDS_VISIBILITY;
    	}
    	*/
    	
    	//if restricted to only self, not allowed
    	if(profilePrivacy.getMyFriends() == ProfileConstants.PRIVACY_OPTION_ONLYME) {
    		return false;
    	}
    	
    	//if user is friend and friends are allowed
    	if(friend && profilePrivacy.getMyFriends() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return true;
    	}
    	
    	//if not friend and set to friends only
    	if(!friend && profilePrivacy.getMyFriends() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return false;
    	}
    	
    	//if everyone is allowed
    	if(profilePrivacy.getMyFriends() == ProfileConstants.PRIVACY_OPTION_EVERYONE) {
    		return true;
    	}
    	    	
    	//uncaught rule, return false
    	log.error("ProfileLogic.isUserXFriendsListVisibleByUserY. Uncaught rule. userX: " + userX + ", userY: " + userY + ", friend: " + friend);   
    	return false;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXStatusVisibleByUserY(String userX, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
		
		//get privacy record for this user
    	ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userX);
    	
    	//pass to main
    	return isUserXStatusVisibleByUserY(userX, profilePrivacy, userY, friend);
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isUserXStatusVisibleByUserY(String userX, ProfilePrivacy profilePrivacy, String userY, boolean friend) {
		
		//if user is requesting own info, they ARE allowed
    	if(userY.equals(userX)) {
    		return true;
    	}
    	
		//if no privacy record, return whatever the flag is set as by default
    	/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.DEFAULT_MYSTATUS_VISIBILITY;
    	}
    	*/
    	
    	//if restricted to only self, not allowed
    	/* DEPRECATED via PRFL-24 when the privacy settings were relaxed
    	if(profilePrivacy.getMyStatus() == ProfilePrivacyManager.PRIVACY_OPTION_ONLYME) {
    		return false;
    	}
    	*/
    	
    	//if user is friend and friends are allowed
    	if(friend && profilePrivacy.getMyStatus() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return true;
    	}
    	
    	//if not friend and set to friends only
    	if(!friend && profilePrivacy.getMyStatus() == ProfileConstants.PRIVACY_OPTION_ONLYFRIENDS) {
    		return false;
    	}
    	
    	//if everyone is allowed
    	if(profilePrivacy.getMyStatus() == ProfileConstants.PRIVACY_OPTION_EVERYONE) {
    		return true;
    	}
    	    	
    	//uncaught rule, return false
    	log.error("ProfileLogic.isUserXStatusVisibleByUserY. Uncaught rule. userX: " + userX + ", userY: " + userY + ", friend: " + friend);   
    	return false;
	}
	
	
	

	/**
 	 * {@inheritDoc}
 	 */
	public boolean isBirthYearVisible(String userId) {
		
		//get privacy record for this user
		ProfilePrivacy profilePrivacy = getPrivacyRecordForUser(userId);
		
		return isBirthYearVisible(profilePrivacy);
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isBirthYearVisible(ProfilePrivacy profilePrivacy) {
		
		//return value or whatever the flag is set as by default
		/* deprecated by PRFL-86, privacy object will never be null now it will always be default or overridden default
    	if(profilePrivacy == null) {
    		return ProfileConstants.DEFAULT_BIRTHYEAR_VISIBILITY;
    	} else {
    	}
    	*/
    	return profilePrivacy.isShowBirthYear();
	}

		
	/**
 	 * {@inheritDoc}
 	 */
	public byte[] getCurrentProfileImageForUser(String userId, int imageType) {
		
		byte[] image = null;
		
		//get record from db
		ProfileImage profileImage = getCurrentProfileImageRecord(userId);
		
		if(profileImage == null) {
			log.debug("ProfileLogic.getCurrentProfileImageForUser() null for userId: " + userId);
			return null;
		}
		
		//get main image
		if(imageType == ProfileConstants.PROFILE_IMAGE_MAIN) {
			image = sakaiProxy.getResource(profileImage.getMainResource());
		}
		
		//or get thumbnail
		if(imageType == ProfileConstants.PROFILE_IMAGE_THUMBNAIL) {
			image = sakaiProxy.getResource(profileImage.getThumbnailResource());
			if(image == null) {
				image = sakaiProxy.getResource(profileImage.getMainResource());
			}
		}
		
		return image;
	}

	/**
 	 * {@inheritDoc}
 	 */
	public ResourceWrapper getCurrentProfileImageForUserWrapped(String userId, int imageType) {
		
		ResourceWrapper resource = new ResourceWrapper();
		
		//get record from db
		ProfileImage profileImage = getCurrentProfileImageRecord(userId);
		
		if(profileImage == null) {
			log.debug("ProfileLogic.getCurrentProfileImageForUserWrapped() null for userId: " + userId);
			return null;
		}
		
		//get main image
		if(imageType == ProfileConstants.PROFILE_IMAGE_MAIN) {
			resource = sakaiProxy.getResourceWrapped(profileImage.getMainResource());
		}
		
		//or get thumbnail
		if(imageType == ProfileConstants.PROFILE_IMAGE_THUMBNAIL) {
			resource = sakaiProxy.getResourceWrapped(profileImage.getThumbnailResource());
			if(resource == null) {
				resource = sakaiProxy.getResourceWrapped(profileImage.getMainResource());
			}
		}
	
		return resource;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean hasUploadedProfileImage(String userId) {
		
		//get record from db
		ProfileImage record = getCurrentProfileImageRecord(userId);
		
		if(record == null) {
			return false;
		}
		return true;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean hasExternalProfileImage(String userId) {
		
		//get record from db
		ProfileImageExternal record = getExternalImageRecordForUser(userId);
		
		if(record == null) {
			return false;
		}
		return true;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public ProfilePreferences createDefaultPreferencesRecord(final String userId) {
		
		ProfilePreferences prefs = getDefaultPreferencesRecord(userId);
		
		//save
		try {
			getHibernateTemplate().save(prefs);
			log.info("Created default preferences record for user: " + userId); 
			return prefs;
		} catch (Exception e) {
			log.error("ProfileLogic.createDefaultPreferencesRecord() failed. " + e.getClass() + ": " + e.getMessage());  
			return null;
		}
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public ProfilePreferences getDefaultPreferencesRecord(final String userId) {
		
		ProfilePreferences prefs = new ProfilePreferences(
				userId,
				ProfileConstants.DEFAULT_EMAIL_REQUEST_SETTING,
				ProfileConstants.DEFAULT_EMAIL_CONFIRM_SETTING,
				ProfileConstants.DEFAULT_EMAIL_PRIVATE_MESSAGE_SETTING,
				ProfileConstants.DEFAULT_TWITTER_SETTING);
		
			return prefs;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public ProfilePreferences getPreferencesRecordForUser(final String userId) {
		
		if(userId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.getPreferencesRecordForUser"); 
	  	}
		
		ProfilePreferences prefs = null;
		
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_PREFERENCES_RECORD);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		prefs = (ProfilePreferences) getHibernateTemplate().execute(hcb);
		
		if(prefs == null) {
			return null;
		}
		
		//decrypt password and set into field
		prefs.setTwitterPasswordDecrypted(ProfileUtils.decrypt(prefs.getTwitterPasswordEncrypted()));
		
		return prefs;
		
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean savePreferencesRecord(ProfilePreferences prefs) {
		
		//validate fields are set and encrypt password if necessary, else clear them all
		if(checkTwitterFields(prefs)) {
			prefs.setTwitterPasswordEncrypted(ProfileUtils.encrypt(prefs.getTwitterPasswordDecrypted()));
		} else {
			prefs.setTwitterEnabled(false);
			prefs.setTwitterUsername(null);
			prefs.setTwitterPasswordDecrypted(null);
			prefs.setTwitterPasswordEncrypted(null);
		}
		
		try {
			getHibernateTemplate().saveOrUpdate(prefs);
			log.info("Updated preferences record for user: " + prefs.getUserUuid()); 
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.savePreferencesRecord() failed. " + e.getClass() + ": " + e.getMessage());  
			return false;
		}
	}
	
	
	
	/**
 	* {@inheritDoc}
 	*/
	public boolean isTwitterIntegrationEnabledForUser(final String userId) {
		
		//check global settings
		if(!sakaiProxy.isTwitterIntegrationEnabledGlobally()) {
			return false;
		}
		
		//check own preferences
		ProfilePreferences profilePreferences = getPreferencesRecordForUser(userId);
		if(profilePreferences == null) {
			return false;
		}
		
		if(profilePreferences.isTwitterEnabled()) {
			return true;
		}
		
		return false;
	}
	
	/**
 	* {@inheritDoc}
 	*/
	public boolean isTwitterIntegrationEnabledForUser(ProfilePreferences prefs) {
		
		//check global settings
		if(!sakaiProxy.isTwitterIntegrationEnabledGlobally()) {
			return false;
		}
		
		//check own prefs
		if(prefs == null) {
			return false;
		}
		
		return prefs.isTwitterEnabled();
	}
	
	
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public void sendMessageToTwitter(final String userId, final String message){
		//setup class thread to call later
		class TwitterUpdater implements Runnable{
			private Thread runner;
			private String username;
			private String password;
			private String message;

			public TwitterUpdater(String username, String password, String message) {
				this.username=username;
				this.password=password;
				this.message=message;
				
				runner = new Thread(this,"Profile2 TwitterUpdater thread"); 
				runner.start();
			}
			

			//do it!
			public synchronized void run() {
				
				Twitter twitter = new Twitter(username, password);
				
				try {
					twitter.setSource(sakaiProxy.getTwitterSource());
					twitter.update(message);
					log.info("Twitter status updated for: " + userId); 
				}
				catch (Exception e) {
					log.error("ProfileLogic.sendMessageToTwitter() failed. " + e.getClass() + ": " + e.getMessage());  
				}
			}
		}
		
		//get preferences for this user
		ProfilePreferences profilePreferences = getPreferencesRecordForUser(userId);
		
		if(profilePreferences == null) {
			return;
		}
		//get details
		String username = profilePreferences.getTwitterUsername();
		String password = profilePreferences.getTwitterPasswordDecrypted();
		
		//instantiate class to send the data
		new TwitterUpdater(username, password, message);
		
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean validateTwitterCredentials(final String twitterUsername, final String twitterPassword) {
		
		if(StringUtils.isNotBlank(twitterUsername) && StringUtils.isNotBlank(twitterPassword)) {
			Twitter twitter = new Twitter(twitterUsername, twitterPassword);
			if(twitter.verifyCredentials()) {
				return true;
			}
		}
		return false;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean validateTwitterCredentials(ProfilePreferences prefs) {
		
		String twitterUsername = prefs.getTwitterUsername();
		String twitterPassword = prefs.getTwitterPasswordDecrypted();
		return validateTwitterCredentials(twitterUsername, twitterPassword);
	}

	
		
	/**
 	 * {@inheritDoc}
 	 */
	/*
	public String generateTinyUrl(final String url) {
		return tinyUrlService.generateTinyUrl(url);
	}
	*/
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean isEmailEnabledForThisMessageType(final String userId, final int messageType) {
		
		//get preferences record for this user
    	ProfilePreferences profilePreferences = getPreferencesRecordForUser(userId);
    	
    	//if none, return whatever the flag is set as by default
    	if(profilePreferences == null) {
    		return ProfileConstants.DEFAULT_EMAIL_NOTIFICATION_SETTING;
    	}
    	
    	//if its a request and requests enabled, true
    	if(messageType == ProfileConstants.EMAIL_NOTIFICATION_REQUEST && profilePreferences.isRequestEmailEnabled()) {
    		return true;
    	}
    	
    	//if its a confirm and confirms enabled, true
    	if(messageType == ProfileConstants.EMAIL_NOTIFICATION_CONFIRM && profilePreferences.isConfirmEmailEnabled()) {
    		return true;
    	}
    	
    	//if its a private message and private messages enabled, true
    	if(messageType == ProfileConstants.EMAIL_NOTIFICATION_PRIVATE_MESSAGE && profilePreferences.isPrivateMessageEmailEnabled()) {
    		return true;
    	}
    	
    	//add more cases here as need progresses
    	
    	//no notification for this message type, return false 	
    	log.debug("ProfileLogic.isEmailEnabledForThisMessageType. False for userId: " + userId + ", messageType: " + messageType);  

    	return false;
		
	}

	
	/**
 	 * {@inheritDoc}
 	 */
	public ProfileImageExternal getExternalImageRecordForUser(final String userId) {
		
		if(userId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.getExternalImageRecordForUser"); 
	  	}
		
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_EXTERNAL_IMAGE_RECORD);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		return (ProfileImageExternal) getHibernateTemplate().execute(hcb);
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public String getExternalImageUrl(final String userId, final int imageType) {
		
		//get external image record for this user
		ProfileImageExternal externalImage = getExternalImageRecordForUser(userId);
		
		//if none, return null
    	if(externalImage == null) {
    		return null;
    	}
    	
    	//else return the url for the type they requested
    	if(imageType == ProfileConstants.PROFILE_IMAGE_MAIN) {
    		String url = externalImage.getMainUrl();
    		if(StringUtils.isBlank(url)) {
    			return null;
    		}
    		return url;
    	}
    	
    	if(imageType == ProfileConstants.PROFILE_IMAGE_THUMBNAIL) {
    		String url = externalImage.getThumbnailUrl();
    		if(StringUtils.isBlank(url)) {
    			url = externalImage.getMainUrl();
    			if(StringUtils.isBlank(url)) {
    				return null;
    			}
    			return url;
    		}
    		return url;
    	}
    	
    	//no notification for this message type, return false 	
    	log.error("ProfileLogic.getExternalImageUrl. No URL for userId: " + userId + ", imageType: " + imageType);  

    	return null;
		
	}

	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean saveExternalImage(final String userId, final String mainUrl, final String thumbnailUrl) {
		
		//make an object out of the params
		ProfileImageExternal ext = new ProfileImageExternal(userId, mainUrl, thumbnailUrl);
		
		try {
			getHibernateTemplate().saveOrUpdate(ext);
			log.info("Updated external image record for user: " + ext.getUserUuid()); 
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.setExternalImage() failed. " + e.getClass() + ": " + e.getMessage());  
			return false;
		}
	}

	
		
	/**
 	 * {@inheritDoc}
 	 */
	public ResourceWrapper getURLResourceAsBytes(String url) {
		
		ResourceWrapper wrapper = new ResourceWrapper();
		
		try {
			URL u = new URL(url);
			
			URLConnection uc = u.openConnection();
			int contentLength = uc.getContentLength();
			String contentType = uc.getContentType();
			uc.setReadTimeout(5000); //timeout of 5 sec just to be on the safe side.
			
			InputStream in = new BufferedInputStream(uc.getInputStream());
			byte[] data = new byte[contentLength];
			
			int bytes = 0;
			int offset = 0;
		      
			while (offset < contentLength) {
				bytes = in.read(data, offset, data.length - offset);
				if (bytes == -1) {
					break;
				}
				offset += bytes;
			}
			in.close();
			
			if (offset != contentLength) {
				throw new IOException("Only read " + offset + " bytes; Expected " + contentLength + " bytes.");
			}
			
			wrapper.setBytes(data);
			wrapper.setMimeType(contentType);
			wrapper.setLength(contentLength);
			wrapper.setExternal(true);
			wrapper.setResourceID(url);
			
		} catch (Exception e) {
			log.error("Failed to retrieve resource: " + e.getClass() + ": " + e.getMessage());
		} 
		return wrapper;
	}

	/**
 	 * {@inheritDoc}
 	 */
	public String getUnavailableImageURL() {
		StringBuilder path = new StringBuilder();
		path.append(sakaiProxy.getServerUrl());
		path.append(ProfileConstants.UNAVAILABLE_IMAGE_FULL);
		return path.toString();
	}

	
	/**
 	 * {@inheritDoc}
 	 */
	public int getAllUnreadMessagesCount(final String userId) {
		
		int count = 0;
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  			Query q = session.getNamedQuery(QUERY_GET_ALL_UNREAD_MESSAGES_COUNT);
	  			q.setParameter(TO, userId, Hibernate.STRING);
	  			q.setBoolean("false", Boolean.FALSE);
	  			return q.uniqueResult();
	  		}
	  	};
	  	
	  	count = ((Integer)getHibernateTemplate().execute(hcb)).intValue();
	  	return count;
	}
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public int getThreadsWithUnreadMessagesCount(final String userId) {
		
		int count = 0;
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  			Query q = session.getNamedQuery(QUERY_GET_THREADS_WITH_UNREAD_MESSAGES_COUNT);
	  			q.setParameter(TO, userId, Hibernate.STRING);
	  			q.setBoolean("false", Boolean.FALSE);
	  			return q.uniqueResult();
	  		}
	  	};
	  	
	  	count = ((Integer)getHibernateTemplate().execute(hcb)).intValue();
	  	return count;
	}
	
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean sendPrivateMessage(Message message) {
		
		//add the extra fields
		message.setRead(false);
		message.setDatePosted(new Date());
		if(StringUtils.isBlank(message.getSubject())) {
			message.setSubject(ProfileConstants.DEFAULT_PRIVATE_MESSAGE_SUBJECT);
		}
		
		//save the message
		try {
			getHibernateTemplate().saveOrUpdate(message);
			log.info("Message sent: " + message.toString()); 
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.sendPrivateMessage() failed. " + e.getClass() + ": " + e.getMessage()); 
			return false;
		}
			
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public List<MessageThread> getMessageThreads(final String userId) {
		
		List<MessageThread> threads = new ArrayList<MessageThread>();
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  		
	  			Query q = session.getNamedQuery(QUERY_GET_MESSAGE_THREADS);
	  			q.setParameter(TO, userId, Hibernate.STRING);
	  			return q.list();
	  		}
	  	};
	  	
	  	threads = (List<MessageThread>) getHibernateTemplate().executeFind(hcb);
	  	return threads;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public int getMessageThreadsCount(final String userId) {
		
		int count = 0;
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  		
	  			Query q = session.getNamedQuery(QUERY_GET_MESSAGE_THREADS_COUNT);
	  			q.setParameter(TO, userId, Hibernate.STRING);
	  			return q.uniqueResult();
	  		}
	  	};
	  	
	  	count = ((Integer)getHibernateTemplate().execute(hcb)).intValue();
	  	return count;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public List<Message> getMessagesInThread(final String threadId) {
		
		List<Message> messages = new ArrayList<Message>();
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  		
	  			Query q = session.getNamedQuery(QUERY_GET_MESSAGES_IN_THREAD);
	  			q.setParameter(THREAD, threadId, Hibernate.STRING);
	  			return q.list();
	  		}
	  	};
	  	
	  	messages = (List<Message>) getHibernateTemplate().executeFind(hcb);
	
	  	return messages;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public int getMessagesInThreadCount(final String threadId) {
		
		int count = 0;
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  		
	  			Query q = session.getNamedQuery(QUERY_GET_MESSAGES_IN_THREAD_COUNT);
	  			q.setParameter(THREAD, threadId, Hibernate.STRING);
	  			return q.uniqueResult();
	  		}
	  	};
	  	
	  	count = ((Integer)getHibernateTemplate().execute(hcb)).intValue();
	  	return count;
	}
	
	
	
	/**
 	 * {@inheritDoc}
 	 */
	public Message getMessage(final long id) {
		
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_MESSAGE);
	  			q.setParameter(ID, id, Hibernate.LONG);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		return (Message) getHibernateTemplate().execute(hcb);
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public MessageThread getMessageThread(final String threadId) {
		
		MessageThread thread = null;
		
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_THREAD);
	  			q.setParameter(ID, threadId, Hibernate.STRING);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		thread = (MessageThread)getHibernateTemplate().execute(hcb);
		if(thread == null) {
			return null;
		}
		
		//add the latest message for this thread
		thread.setMostRecentMessageId(getLatestMessageInThread(threadId));
		
		return thread;
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public Message getLatestMessageInThread(final String threadId) {
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_LATEST_MESSAGE_IN_THREAD);
	  			q.setParameter(ID, threadId, Hibernate.STRING);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		return (Message) getHibernateTemplate().execute(hcb);
	}

	
	/**
 	 * {@inheritDoc}
 	 */
	public boolean toggleMessageRead(Message message, final boolean read) {
		try {
			message.setRead(read);
			getHibernateTemplate().saveOrUpdate(message);
			return true;
		} catch (Exception e) {
			log.error("ProfileLogic.toggleMessageRead() failed. " + e.getClass() + ": " + e.getMessage());
			return false;
		}
	}

	/**
 	 * {@inheritDoc}
 	 */
	public boolean toggleAllMessagesInThreadAsRead(final String threadId, final String userUuid, final boolean read) {
		// TODO Auto-generated method stub
		return false;
	}
	
	

	

	

	
	/**
	 * Get a list of unconfirmed Friend requests for a given user. Uses a native SQL query
	 * Returns: (all those where userId is the friend_uuid and confirmed=false)
	 *
	 * @param userId		uuid of the user to retrieve the list of friends for
	 */
	private List<String> getRequestedConnectionUserIdsForUser(final String userId) {
		
		if(userId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.getFriendRequestsForUser()"); 
	  	}
		
		List<String> requests = new ArrayList<String>();
		
		//get friends of this user [and map it automatically to the Friend object]
		//updated: now just returns a List of Strings
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  			Query q = session.getNamedQuery(QUERY_GET_FRIEND_REQUESTS_FOR_USER);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setBoolean("false", Boolean.FALSE); 
	  			//q.setResultTransformer(Transformers.aliasToBean(Friend.class));
	  			
	  			return q.list();
	  		}
	  	};
	  	
	  	requests = (List<String>) getHibernateTemplate().executeFind(hcb);
	  	
	  	return requests;
	}
	
	/**
	 * Get a list of confirmed connections for a given user. Uses a native SQL query so we can use unions
	 * Returns: (all those where userId is the user_uuid and confirmed=true) & (all those where user is friend_uuid and confirmed=true)
	 *
	 * This only returns userIds. If you want a list of Person objects, see getConnectionsForUser()
	 * 
	 * @param userId		uuid of the user to retrieve the list of friends for
	 */
	private List<String> getConfirmedConnectionUserIdsForUser(final String userId) {
		
		List<String> userUuids = new ArrayList<String>();
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  		
	  			Query q = session.getNamedQuery(QUERY_GET_CONFIRMED_FRIEND_USERIDS_FOR_USER);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setBoolean("true", Boolean.TRUE); 
	  			return q.list();
	  		}
	  	};
	  	
	  	userUuids = (List<String>) getHibernateTemplate().executeFind(hcb);
	
	  	return userUuids;
	}
	
	
	
	
	// helper method to check if all required twitter fields are set properly
	private boolean checkTwitterFields(ProfilePreferences prefs) {
		return (prefs.isTwitterEnabled() &&
				StringUtils.isNotBlank(prefs.getTwitterUsername()) &&
				StringUtils.isNotBlank(prefs.getTwitterPasswordDecrypted()));
	}
	
	
	
	//private method to query SakaiPerson for matches
	//this should go in the profile ProfilePersistence API
	private List<String> findSakaiPersonsByNameOrEmail(final String search) {
		
		List<String> userUuids = new ArrayList<String>();
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  			Query q = session.getNamedQuery(QUERY_FIND_SAKAI_PERSONS_BY_NAME_OR_EMAIL);
	  			q.setParameter(SEARCH, '%' + search + '%', Hibernate.STRING);
	  			return q.list();
	  		}
	  	};
	  	
	  	userUuids = (List<String>) getHibernateTemplate().executeFind(hcb);
	
	  	return userUuids;
	}
	
	
	//private method to query SakaiPerson for matches
	//this should go in the profile ProfilePersistence API
	private List<String> findSakaiPersonsByInterest(final String search) {
		
		List<String> userUuids = new ArrayList<String>();
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  			Query q = session.getNamedQuery(QUERY_FIND_SAKAI_PERSONS_BY_INTEREST);
	  			q.setParameter(SEARCH, '%' + search + '%', Hibernate.STRING);
	  			return q.list();
	  		}
	  	};
	  	
	  	userUuids = (List<String>) getHibernateTemplate().executeFind(hcb);
	
	  	return userUuids;
	}


	
	
	
	/**
	 * Get the current ProfileImage records from the database.
	 * There should only ever be one, but in case things get out of sync this returns all.
	 * This method is only used when we are adding a new image as we need to invalidate all of the others
	 * If you are just wanting to retrieve the latest image, see getCurrentProfileImageRecord()
	 *
	 * @param userId		userId of the user
	 */
	private List<ProfileImage> getCurrentProfileImageRecords(final String userId) {
		
		List<ProfileImage> images = new ArrayList<ProfileImage>();
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  			Query q = session.getNamedQuery(QUERY_GET_CURRENT_PROFILE_IMAGE_RECORD);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			return q.list();
	  		}
	  	};
	  	
	  	images = (List<ProfileImage>) getHibernateTemplate().executeFind(hcb);
	  	
	  	return images;
	}


	/**
	 * Get the current ProfileImage record from the database.
	 * There should only ever be one, but if there are more this will return the latest. 
	 * This is called when retrieving a profile image for a user. When adding a new image, there is a call
	 * to a private method called getCurrentProfileImageRecords() which should invalidate any multiple current images
	 *
	 * @param userId		userId of the user
	 */
	private ProfileImage getCurrentProfileImageRecord(final String userId) {
		
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_CURRENT_PROFILE_IMAGE_RECORD);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		return (ProfileImage) getHibernateTemplate().execute(hcb);
	}
	
	
	/**
	 * Get old ProfileImage records from the database. 
	 * TODO: Used for displaying old the profile pictures album
	 *
	 * @param userId		userId of the user
	 */
	private List<ProfileImage> getOtherProfileImageRecords(final String userId) {
		
		List<ProfileImage> images = new ArrayList<ProfileImage>();
		
		//get 
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			
	  			Query q = session.getNamedQuery(QUERY_OTHER_PROFILE_IMAGE_RECORDS);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			return q.list();
	  		}
	  	};
	  	
	  	images = (List<ProfileImage>) getHibernateTemplate().executeFind(hcb);
	  	
	  	return images;
	}


	//gets a friend record and tries both column arrangements
	private ProfileFriend getFriendRecord(final String userId, final String friendId) {
		
		if(userId == null || friendId == null){
	  		throw new IllegalArgumentException("Null Argument in ProfileLogic.getFriendRecord"); 
	  	}
		
		ProfileFriend profileFriend = null;
		
		//this particular query checks for records when userId/friendId is in either column
		HibernateCallback hcb = new HibernateCallback() {
	  		public Object doInHibernate(Session session) throws HibernateException, SQLException {
	  			Query q = session.getNamedQuery(QUERY_GET_FRIEND_RECORD);
	  			q.setParameter(USER_UUID, userId, Hibernate.STRING);
	  			q.setParameter(FRIEND_UUID, friendId, Hibernate.STRING);
	  			q.setMaxResults(1);
	  			return q.uniqueResult();
			}
		};
	
		profileFriend = (ProfileFriend) getHibernateTemplate().execute(hcb);
	
		return profileFriend;
	}
	
	
	//private method to back the search methods. returns only uuids which then need to be checked for privacy settings etc.
	private List<String> findUsersByNameOrEmail(String search) {
		
		//get users from SakaiPerson
		List<String> userUuids = new ArrayList<String>(findSakaiPersonsByNameOrEmail(search));

		//get users from UserDirectoryService
		List<String> usersUuidsFromUserDirectoryService = new ArrayList<String>(sakaiProxy.searchUsers(search));
		
		//combine with no duplicates
		userUuids.removeAll(usersUuidsFromUserDirectoryService);
		userUuids.addAll(usersUuidsFromUserDirectoryService);
		
		return userUuids;
	
	}
	
	
	//private utility method used by findUsersByNameOrEmail() and findUsersByInterest() to format results from
	//the supplied userUuids to SearchResult records based on friend or friendRequest status and the privacy settings for each user
	//that was in the initial search results
	private List<SearchResult> createSearchResultRecordsFromSearch(List<String> userUuids, String userId) {

		List<SearchResult> results = new ArrayList<SearchResult>();
			
		//TODO get the list of Users via getUsers(userUuids) instead of individually in the iterator below?
		//Is this an issue? Its cached?
		
		//get type of requesting user so we can check if they are allowed to connect to the users found
		String searchingUserType = sakaiProxy.getUserType(userId);
		
		//for each userUuid, is userId a friend?
		//also, get privacy record for the userUuid. if searches not allowed for this user pair, skip to next
		//otherwise create SearchResult record and add to list
		for(Iterator<String> i = userUuids.iterator(); i.hasNext();){
			String userUuid = (String)i.next();
			
			//if user is in the list of invisible users, skip unless current user is admin
			if(!sakaiProxy.isSuperUser() && sakaiProxy.getInvisibleUsers().contains(userUuid)){
				continue;
			}
			
			//get User object
			User u = sakaiProxy.getUserQuietly(userUuid);
			
			//if they don't exist, skip
			if(u == null) {
				continue;
			}
			
			
			//get User details
			String displayName = u.getDisplayName();
			String userType = u.getType();
			
			//friend?
			boolean friend = isUserXFriendOfUserY(userUuid, userId);
			
			//init request flags
			boolean friendRequestToThisPerson = false;
			boolean friendRequestFromThisPerson = false;
			
			//if not friend, has a friend request already been made to this person?
			if(!friend) {
				friendRequestToThisPerson = isFriendRequestPending(userId, userUuid);
			}
			
			//if not friend and no friend request to this person, has a friend request been made from this person to the current user?
			if(!friend && !friendRequestToThisPerson) {
				friendRequestFromThisPerson = isFriendRequestPending(userUuid, userId);
			}
			
			//get privacy record
			ProfilePrivacy privacy = getPrivacyRecordForUser(userUuid);
			
			//is this user visible in searches by this user? if not, skip unless admin user
			if(!sakaiProxy.isSuperUser() && !isUserXVisibleInSearchesByUserY(userUuid, privacy, userId, friend)) {
				continue; 
			}
			
			//is profile photo visible to this user?
			//is status visible to this user?
			//is friends list visible to this user?
			//is connection allowed between these user types?
			//all true if super user, otherwise run the check.
			boolean profileImageAllowed;
			boolean statusVisible;
			boolean friendsListVisible;
			boolean connectionAllowed;
			
			if (sakaiProxy.isSuperUser()) {
				profileImageAllowed = true;
				statusVisible = true;
				friendsListVisible = true;
				connectionAllowed = true;
			} else {
				profileImageAllowed = isUserXProfileImageVisibleByUserY(userUuid, privacy, userId, friend);
				statusVisible = isUserXStatusVisibleByUserY(userUuid, privacy, userId, friend);
				friendsListVisible = isUserXFriendsListVisibleByUserY(userUuid, privacy, userId, friend);
				connectionAllowed = sakaiProxy.isConnectionAllowedBetweenUserTypes(searchingUserType, userType);
			}	
		
			
			//make object
			SearchResult searchResult = new SearchResult(
					userUuid,
					displayName,
					userType,
					friend,
					profileImageAllowed,
					statusVisible,
					friendsListVisible,
					friendRequestToThisPerson,
					friendRequestFromThisPerson,
					connectionAllowed
					);
			
			results.add(searchResult);
		}
		
		return results;
	}
	
	
	
	//init method called when Tomcat starts up
	public void init() {
		
		log.info("Profile2: init()"); 
		
		//do we need to run the conversion utility?
		if(sakaiProxy.isProfileConversionEnabled()) {
			convertProfile();
		}
	}
	
	
	
	//method to convert profileImages
	private void convertProfile() {
		log.info("Profile2: ==============================="); 
		log.info("Profile2: Conversion utility starting up."); 
		log.info("Profile2: ==============================="); 

		//get list of users
		List<String> allUsers = new ArrayList<String>(listAllSakaiPersons());
		
		if(allUsers.isEmpty()){
			log.info("Profile2 conversion util: No SakaiPersons to process.");
			return;
		}
		//for each, do they have a profile image record. if so, skip (perhaps null the SakaiPerson JPEG_PHOTO bytes?)
		for(Iterator<String> i = allUsers.iterator(); i.hasNext();) {
			String userUuid = (String)i.next();
			
			//only process uploaded image if doesn't already have a record for this
			if(hasUploadedProfileImage(userUuid)) {
				log.info("Profile2 conversion util: ProfileImage record exists for " + userUuid + ". Nothing to do here, skipping to next section...");
			} else {
				log.info("Profile2 conversion util: No existing ProfileImage record for " + userUuid + ". Processing...");
				
				//get photo from SakaiPerson
				byte[] image = sakaiProxy.getSakaiPersonJpegPhoto(userUuid);
				
				//if none, nothing to do
				if(image == null || image.length == 0) {
					log.info("Profile2 conversion util: No image binary to convert for " + userUuid + ". Skipping to next section...");
				} else {
					
					//set some defaults for the image we are adding to ContentHosting
					String fileName = "Profile Image";
					String mimeType = "image/jpeg";
					
					//scale the main image
					byte[] imageMain = ProfileUtils.scaleImage(image, ProfileConstants.MAX_IMAGE_XY);
					
					//create resource ID
					String mainResourceId = sakaiProxy.getProfileImageResourcePath(userUuid, ProfileConstants.PROFILE_IMAGE_MAIN);
					log.info("Profile2 conversion util: mainResourceId: " + mainResourceId);
					
					//save, if error, log and return.
					if(!sakaiProxy.saveFile(mainResourceId, userUuid, fileName, mimeType, imageMain)) {
						log.error("Profile2 conversion util: Saving main profile image failed.");
						continue;
					}
	
					/*
					 * THUMBNAIL PROFILE IMAGE
					 */
					//scale image
					byte[] imageThumbnail = ProfileUtils.scaleImage(image, ProfileConstants.MAX_THUMBNAIL_IMAGE_XY);
					 
					//create resource ID
					String thumbnailResourceId = sakaiProxy.getProfileImageResourcePath(userUuid, ProfileConstants.PROFILE_IMAGE_THUMBNAIL);
	
					log.info("Profile2 conversion util: thumbnailResourceId:" + thumbnailResourceId);
					
					//save, if error, log and return.
					if(!sakaiProxy.saveFile(thumbnailResourceId, userUuid, fileName, mimeType, imageThumbnail)) {
						log.warn("Profile2 conversion util: Saving thumbnail profile image failed. Main image will be used instead.");
						thumbnailResourceId = null;
					}
	
					/*
					 * SAVE IMAGE RESOURCE IDS
					 */
					if(addNewProfileImage(userUuid, mainResourceId, thumbnailResourceId)) {
						log.info("Profile2 conversion util: Binary image converted and saved for " + userUuid);
					} else {
						log.warn("Profile2 conversion util: Binary image conversion failed for " + userUuid);
					}
				}
			} 
			
			//process any image URLs, if they don't already have a valid record.
			if(hasExternalProfileImage(userUuid)) {
				log.info("Profile2 conversion util: ProfileImageExternal record exists for " + userUuid + ". Nothing to do here, skipping...");
			} else {
				log.info("Profile2 conversion util: No existing ProfileImageExternal record for " + userUuid + ". Processing...");
				
				String url = sakaiProxy.getSakaiPersonImageUrl(userUuid);
				
				//if none, nothing to do
				if(StringUtils.isBlank(url)) {
					log.info("Profile2 conversion util: No url image to convert for " + userUuid + ". Skipping...");
				} else {
					if(saveExternalImage(userUuid, url, null)) {
						log.info("Profile2 conversion util: Url image converted and saved for " + userUuid);
					} else {
						log.warn("Profile2 conversion util: Url image conversion failed for " + userUuid);
					}
				}
				
			}
			
			log.info("Profile2 conversion util: Finished converting user profile for: " + userUuid);
			//go to next user
		}
		
		return;
	}
	
	
	//setup SakaiProxy API
	private SakaiProxy sakaiProxy;
	public void setSakaiProxy(SakaiProxy sakaiProxy) {
		this.sakaiProxy = sakaiProxy;
	}

	
	//setup TinyUrlService API
	/*
	private TinyUrlService tinyUrlService;
	public void setTinyUrlService(TinyUrlService tinyUrlService) {
		this.tinyUrlService = tinyUrlService;
	}
	*/
	
	
	
}
