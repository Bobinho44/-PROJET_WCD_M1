package foo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PropertyProjection;
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

public class ScoreEndpoint {

    @ApiMethod(name = "likePost", httpMethod = HttpMethod.POST)
	public void likePost(User user, TinyLikeInfo likeInfo) throws UnauthorizedException, EntityNotFoundException {
		
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            
        //Gets the post to like
        Entity tinyPost = datastore.get(KeyFactory.createKey("Post", likeInfo.id));
            
        //Creates the like index
        Entity tinyLikeIndex = new Entity("LikeIndex", user.getId() + "/" + likeInfo.id);
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
                
                    //Creates a new like counter
                    Entity newTinyLikeCounter = new Entity("LikeCounter", tinyPost.getKey() + "/" + user.getId());
                    newTinyLikeCounter.setProperty("post", tinyPost.getKey());
                    newTinyLikeCounter.setProperty("likes", 1);
                    datastore.put(newTinyLikeCounter);
                    liked = true;
                }
            }
                
            //The transaction has been commited
            else {
                liked = true;
            }
        }
                
        //Starts transaction
        Transaction transaction = datastore.beginTransaction();
                
        tinyPost.setProperty("likes", getLikes(user, tinyPost.getKey()));
        datastore.put(tinyPost);
                
        transaction.commit();
                
        //Checks if the transaction has been commited
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    private long getLikes(User user, Key post) throws UnauthorizedException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Gets all like counter of a post
        Query q = new Query("LikeCounter").setFilter(new FilterPredicate("post", FilterOperator.EQUAL, post));

        //Gets the number of like of a post
        return datastore.prepare(q).asList(FetchOptions.Builder.withLimit(40000)).stream().parallel()
            .mapToLong(counter -> (long) counter.getProperty("likes"))
            .sum();
    }

    private boolean hasLiked(String liker, String post) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Checks if a LikeIndex has the key "liker/post"
        try {
            datastore.get(KeyFactory.createKey("LikeIndex", liker + "/" + post));
            return true;
        }
        catch (EntityNotFoundException e) {
            return false;
        }

    }

    @ApiMethod(name = "createPost", httpMethod = HttpMethod.POST)
	public void createPost(User user, TinyPostInfo postInfo) throws UnauthorizedException, EntityNotFoundException {
		
        //long first = System.currentTimeMillis();
        
        //Checks if the user is registered
        if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        long postDate = new Date().getTime();

        int page = 40001;
        for (int i = 0; page >= 40000; i++) {
            Query q = new Query("FollowIndex").setFilter(new FilterPredicate("followed", FilterOperator.EQUAL, user.getId()));

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

        /**long last = System.currentTimeMillis() - first;

        Entity postTime = new Entity("createPostTime");
        postTime.setProperty("time", last);
        datastore.put(postTime);*/

    }

    @ApiMethod(name = "abcdef", httpMethod = HttpMethod.GET)
	public List<Entity> abcdef(User user) throws UnauthorizedException {
        
        long first = System.currentTimeMillis();

        //Checks if the user is registered
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Gets all post index where the user is a follower of the sender
        Query q = new Query("Post").setFilter(new FilterPredicate("followers", FilterOperator.EQUAL, user.getId()));
        
        //Gets all post where the user is a follower of the sender
        List<Entity> c = datastore.prepare(q).asList(FetchOptions.Builder.withLimit(500));

        if (c.size() == 0) {
            return Collections.emptyList();
        }
        
        long time = Long.valueOf(c.get(c.size() - 1).getKey().getName().split("/")[0]);

        Query q2 = new Query("LikeIndex")
            .setFilter(new FilterPredicate("liker", FilterOperator.EQUAL, user.getId()))
            .setFilter(new FilterPredicate("time", FilterOperator.GREATER_THAN_OR_EQUAL, time))
            .setKeysOnly();
        
        List<String> d = datastore.prepare(q2).asList(FetchOptions.Builder.withLimit(500)).stream()
            .map(like -> like.getKey().getName().split("/")[1] + "/" + like.getKey().getName().split("/")[2])
            .collect(Collectors.toList());

        List<Entity> p = c.stream()
            .map(post ->  {
                    String[] args = ((String) post.getProperty("infos")).split("arg=");
                    //Creates the post informations
                    Entity e = new Entity("SearchedPost");
                    e.setProperty("id", post.getKey().getName());
                    e.setProperty("sender", args[0]);
                    e.setProperty("url", args[1]);
                    e.setProperty("likes", post.getProperty("likes"));
                    e.setProperty("hasLiked", d.contains(post.getKey().getName()));
                    
                    return e;
            })
            .collect(Collectors.toList());

        long last = System.currentTimeMillis() - first;

        Entity postTime = new Entity("createPostTime");
        postTime.setProperty("time", last);
        datastore.put(postTime);

        return p;
    }

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
        
        /**List<String> ids = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            ids.add(RandomStringUtils.random(new Random().nextInt(10), true, true) + RandomStringUtils.random(new Random().nextInt(10), true, true));
        }

        for (String id : ids) {
            String a = String.valueOf(Math.abs(new Random().nextLong()));
            //Creates the user
        Entity tinyUser2 = new Entity("User", a + "arg=" + id + "arg=" + userInfo.picture);
        tinyUser2.setProperty("name", id.toLowerCase());
        datastore.put(tinyUser2);

        //Creates the follow index
        Entity tinyFollowIndex2 = new Entity("FollowIndex", a + "/" + user.getId());
        tinyFollowIndex2.setProperty("follower", a);
        tinyFollowIndex2.setProperty("followed", user.getId());
        datastore.put(tinyFollowIndex2);
        }*/
    }

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
        return datastore.prepare(q).asList(FetchOptions.Builder.withLimit(10)).stream()
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
