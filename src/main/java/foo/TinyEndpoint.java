package foo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

import org.apache.commons.lang3.RandomStringUtils;

import com.google.appengine.api.datastore.Transaction;

@Api(name = "myApi",
     version = "v1",
     audiences = "198559984912-f0hq5dtmguc10ta2lrfrfhlmmtgdg805.apps.googleusercontent.com",
  	 clientIds = {"198559984912-f0hq5dtmguc10ta2lrfrfhlmmtgdg805.apps.googleusercontent.com",
        "927375242383-jm45ei76rdsfv7tmjv58tcsjjpvgkdje.apps.googleusercontent.com"},
     namespace =
     @ApiNamespace(
		   ownerDomain = "wcd-cloud-datastore-projet.appspot.com",
		   ownerName = "wcd-cloud-datastore-projet.appspot.com",
		   packagePath = "")
     )

public class TinyEndpoint {

    /**
     * Likes a post
     * @param user the user
     * @param likeInfo the like info wrapper containing the post id
     * @throws UnauthorizedException the user is invalid
     * @throws EntityNotFoundException the post id is invalid
     */
    @ApiMethod(name = "likePost", httpMethod = HttpMethod.POST)
	public void likePost(User user, TinyLikeInfo likeInfo) throws UnauthorizedException, EntityNotFoundException {
		
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            
        //Gets the post to like
        Entity tinyPost = datastore.get(KeyFactory.createKey("Post", likeInfo.id));
            
        //Creates the like index
        Entity tinyLikeIndex = new Entity("LikeIndex", user.getId() + "arg=" + likeInfo.id);
        tinyLikeIndex.setProperty("liker", user.getId());
        tinyLikeIndex.setProperty("time", tinyPost.getKey().getName().split("/")[0]);
        datastore.put(tinyLikeIndex);
            
        //Gets all like counter associated with the post
        Query q = new Query("LikeCounter").setFilter(new FilterPredicate("post", FilterOperator.EQUAL, tinyPost.getKey()));
                    
        List<Entity> counters = datastore.prepare(q).asList(FetchOptions.Builder.withLimit(40000));
            
        boolean liked = false;
            
        //Likes the post
        while (!liked) {
            
            //Selects a random counter
            Entity tinyLikeCounter = counters.get(new Random().nextInt(counters.size()));
                
            //Starts transaction
            Transaction transaction = datastore.beginTransaction();
                
            long likes = (long) tinyLikeCounter.getProperty("likes");
                
            tinyLikeCounter.setProperty("likes", likes + 1);
            datastore.put(tinyLikeCounter);
                
            transaction.commit();
                
            //Checks if the transaction has been commited
            if (transaction.isActive()) {
                transaction.rollback();
                
                //Checks if the counter limit hasn't be reached
                if (counters.size() < 40000) {
                    
                    while (!liked) {
                        Transaction t = datastore.beginTransaction();

                        //Creates a new like counter
                        Entity newTinyLikeCounter = new Entity("LikeCounter", tinyPost.getKey().getName() + "/" + user.getId());
                        newTinyLikeCounter.setProperty("post", tinyPost.getKey());
                        newTinyLikeCounter.setProperty("likes", 1);
                        datastore.put(newTinyLikeCounter);

                        t.commit();
                
                        //Checks if the transaction has been commited
                        if (!t.isActive()) {
                            liked = true;
                        }
                    }
                }
            }
                
            //The transaction has been commited
            else {
                liked = true;
            }
        }
                
        //Starts transaction
        Transaction transaction = datastore.beginTransaction();
                
        //Gets all like counter of a post
        Query q3 = new Query("LikeCounter").setFilter(new FilterPredicate("post", FilterOperator.EQUAL, tinyPost.getKey()));

        //Gets the number of like of a post
        long likes = datastore.prepare(q3).asList(FetchOptions.Builder.withLimit(40000)).stream().parallel()
            .mapToLong(counter -> (long) counter.getProperty("likes"))
            .sum();

        tinyPost.setProperty("likes", likes);
        datastore.put(tinyPost);
                
        transaction.commit();
                
        //Checks if the transaction has been commited
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    /**
     * Creates a new post
     * 
     * @param user the user
     * @param postInfo the post info wrapper containing the sender and the url
     * @throws UnauthorizedException the user is invalid
     */
    @ApiMethod(name = "createPost", httpMethod = HttpMethod.POST)
	public void createPost(User user, TinyPostInfo postInfo) throws UnauthorizedException {
        
        //Checks if the user is registered
        if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        long postDate = new Date().getTime();

        //Creates a post for all list of 40000 followers
        int page = 40001;
        for (int i = 0; page >= 40000; i++) {
            Query q = new Query("FollowIndex").setFilter(new FilterPredicate("followed", FilterOperator.EQUAL, user.getId()));

            //Collects a list of 40000 followers
            List<String> followers = datastore.prepare(q).asList(FetchOptions.Builder.withLimit(40000).offset(i * 40000)).stream().parallel()
                .map(follower -> (String) follower.getProperty("follower"))
                .collect(Collectors.toList());

            page = followers.size();
            
            //Creates the post
            Entity tinyPost = new Entity("Post", Long.MAX_VALUE - postDate + "/" + UUID.randomUUID().toString());
            tinyPost.setProperty("likes", 0L);
            tinyPost.setProperty("infos", postInfo.sender + "arg=" + postInfo.url);
            tinyPost.setProperty("followers", new ArrayList<>(followers));
            datastore.put(tinyPost);

            //Creates the like counter
            Entity tinyLikeCounter = new Entity("LikeCounter", tinyPost.getKey() + "/initialCounter");
            tinyLikeCounter.setProperty("post", tinyPost.getKey());
            tinyLikeCounter.setProperty("likes", 0);
            datastore.put(tinyLikeCounter);
        }
    }

    /**
     * Gets the last 10 posts (the function does not work with a real name like getLastPosts))
     * 
     * @param user the user
     * @return the last 10 posts
     * @throws UnauthorizedException the user is invalid
     */
    @ApiMethod(name = "abcdef", httpMethod = HttpMethod.GET)
	public List<Entity> abcdef(User user) throws UnauthorizedException {

        //Checks if the user is registered
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Gets all post index where the user is a follower of the sender
        Query q = new Query("Post").setFilter(new FilterPredicate("followers", FilterOperator.EQUAL, user.getId()));
        
        //Gets all post where the user is a follower of the sender
        List<Entity> c = datastore.prepare(q).asList(FetchOptions.Builder.withLimit(10));

        if (c.size() == 0) {
            return Collections.emptyList();
        }
        
        long time = Long.valueOf(c.get(c.size() - 1).getKey().getName().split("/")[0]);

        //Gets all last liked post by the user
        Query q2 = new Query("LikeIndex")
            .setFilter(new FilterPredicate("liker", FilterOperator.EQUAL, user.getId()))
            .setFilter(new FilterPredicate("time", FilterOperator.GREATER_THAN_OR_EQUAL, time))
            .setKeysOnly();
        
        //Collects all post from user's likes
        List<String> d = datastore.prepare(q2).asList(FetchOptions.Builder.withLimit(10)).stream().parallel()
            .map(like -> like.getKey().getName())
            .collect(Collectors.toList());
        

        AtomicInteger likedNumber = new AtomicInteger(0);

        //Gets last posts
        return c.stream()
            .map(post -> {
                String[] args = ((String) post.getProperty("infos")).split("arg=");
                String key = post.getKey().getName();
                boolean liked = likedNumber.get() >= d.size() ? false : d.contains(user.getId() + "arg=" + key);
                if (liked) likedNumber.incrementAndGet();

                //Creates the post informations
                Entity e = new Entity("SearchedPost");
                e.setProperty("id", key);
                e.setProperty("sender", args[0]);
                e.setProperty("url", args[1]);
                e.setProperty("likes", post.getProperty("likes"));
                e.setProperty("hasLiked", liked);

                return e;
            })
            .collect(Collectors.toList());
    }

    /**
     * Registers a user
     * 
     * @param user the user
     * @param userInfo the user info wrapper containing the user name and his picture url
     * @throws UnauthorizedException the user is invalid
     */
    @ApiMethod(name = "registerUser", httpMethod = HttpMethod.POST)
	public void registerUser(User user, TinyUserInfo userInfo) throws UnauthorizedException {
  
        //Checks if the user is registered
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Creates the user
        Entity tinyUser = new Entity("User", user.getId() + "arg=" + userInfo.name + "arg=" + userInfo.picture);
        tinyUser.setProperty("name", userInfo.name.toLowerCase());
        datastore.put(tinyUser);

        //Creates the follow index
        Entity tinyFollowIndex = new Entity("FollowIndex", user.getId() + "/" + user.getId());
        tinyFollowIndex.setProperty("follower", user.getId());
        tinyFollowIndex.setProperty("followed", user.getId());
        datastore.put(tinyFollowIndex);
    }

    /**
     * Searchs user from his name
     * 
     * @param user the user
     * @param searchUserInfo the search user info wrapper containing the name of the searched user
     * @return a list of compatible users
     * @throws UnauthorizedException the user is invalid
     */
    @ApiMethod(name = "searchUsers", httpMethod = HttpMethod.GET)
	public List<Entity> searchUsers(User user, TinySearchUserInfo searchUserInfo) throws UnauthorizedException {
		
        //Checks if the user is registered
        if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Checks if the reasearch is not empty
        if (searchUserInfo.name.equals("")) {
            return Collections.emptyList();
        }
        
        //Gets all user with a name greater than the reasearched name (lexicographical order)
		Query q = new Query("User").setFilter(new FilterPredicate("name", FilterOperator.GREATER_THAN_OR_EQUAL, searchUserInfo.name));

		//Gets all user compatible with the research
        return datastore.prepare(q).asList(FetchOptions.Builder.withLimit(10)).stream().parallel()
            .map(searchedUser -> {

                String[] args = searchedUser.getKey().getName().split("arg=");

                //Creates the user informations
                Entity e = new Entity("SearchedUser");
                e.setProperty("id", args[0]);
                e.setProperty("name", args[1]);
                e.setProperty("picture", args[2]);
                e.setProperty("isFollower", isFollower(user.getId(), args[0]));

                return e;
            }).collect(Collectors.toList());
	}
    
    /**
     * Follows a user
     * 
     * @param user the user
     * @param followInfo the follow info wrapper containing the followed id
     * @throws UnauthorizedException the user is invalid
     */
    @ApiMethod(name = "followUser", httpMethod = HttpMethod.POST)
	public void followUser(User user, TinyFollowInfo followInfo) throws UnauthorizedException {

        //Checks if the user is registered
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Creates the follow index
        Entity tinyFollowIndex = new Entity("FollowIndex", user.getId() + "/" + followInfo.id);
        tinyFollowIndex.setProperty("follower", user.getId());
        tinyFollowIndex.setProperty("followed", followInfo.id);
        datastore.put(tinyFollowIndex);
    }

    /**
     * Checks if the the follower follow the followed
     * 
     * @param follower the follower
     * @param followed the followed
     * @return true if the the follower follow the followed, false otherwise
     */
    private boolean isFollower(String follower, String followed) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Checks if a followIndex has the key "follower/followed"
        try {
            datastore.get(KeyFactory.createKey("FollowIndex", follower + "/" + followed));
            return true;
        }
        catch (EntityNotFoundException e) {
            return false;
        }
    }

}