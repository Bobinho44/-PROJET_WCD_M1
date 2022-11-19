package foo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.UnauthorizedException;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
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
		
        //Checks if the user is registered
        if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Gets the post to like
        Entity tinyPost = datastore.get(KeyFactory.createKey("Post", likeInfo.id));

        //Creates the like index
        Entity tinyLikeIndex = new Entity("LikeIndex", user.getId() + "/" + likeInfo.id);
        tinyLikeIndex.setProperty("post", tinyPost.getKey());
        tinyLikeIndex.setProperty("liker", user.getId());
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
                    newTinyLikeCounter.setProperty("likes", 1L);
                    datastore.put(newTinyLikeCounter);
                    liked = true;
                }
            }

            //The transaction has been commited
            else {
                liked = true;
            }
        }
    }

    private long getLikes(User user, Entity post) throws UnauthorizedException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Gets all like counter of a post
        Query q = new Query("LikeCounter").setFilter(new FilterPredicate("post", FilterOperator.EQUAL, post.getKey()));

        //Gets the number of like of a post
        return datastore.prepare(q).asList(FetchOptions.Builder.withLimit(40000)).stream()
            .mapToLong(counter -> (long) counter.getProperty("likes"))
            .sum();
    }

    private boolean hasLiked(String liker, long post) throws UnauthorizedException {
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
		
        //Checks if the user is registered
        if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Creates the post
        Entity tinyPost = new Entity("Post");
        tinyPost.setProperty("url", postInfo.url);
        tinyPost.setProperty("sender",  user.getId());
        datastore.put(tinyPost);

        //Creates the like counter
        Entity tinyLikeCounter = new Entity("LikeCounter", tinyPost.getKey() + "/initialCounter");
        tinyLikeCounter.setProperty("post", tinyPost.getKey());
        tinyLikeCounter.setProperty("likes", 0L);
        datastore.put(tinyLikeCounter);

        List<String> followers;
        int followersNumber = 1;
        long postDate = new Date().getTime();
        //Optional<Cursor> cursor = Optional.empty();

        //Gets all user followers and create post index
        while (followersNumber >= 1) {
            Query q = new Query("FollowIndex").setFilter(new FilterPredicate("followed", FilterOperator.EQUAL, user.getId()));

            PreparedQuery pq = datastore.prepare(q);

            /**QueryResultList<Entity> result = cursor.map(c -> pq.asQueryResultList(FetchOptions.Builder.withLimit(40000).startCursor(c))).orElse(pq.asQueryResultList(FetchOptions.Builder.withLimit(40000)));

            cursor = Optional.of(result.getCursor());

            followers = result.stream()
                .map(follower -> (String) follower.getProperty("follower"))
                .collect(Collectors.toList());

            followersNumber = result.size();*/

            followers = pq.asList(FetchOptions.Builder.withLimit(40000)).stream()
                .map(follower -> (String) follower.getProperty("follower"))
                .collect(Collectors.toList());

            followersNumber = 0;

            //Creates the postIndex
            Entity tinyPostIndex = new Entity("PostIndex", Long.MAX_VALUE - postDate + "/" + user.getId());
            tinyPostIndex.setProperty("post", tinyPost.getKey());
            tinyPostIndex.setProperty("followers", new ArrayList<>(followers));
            datastore.put(tinyPostIndex);
        }
    }

    //TODO getPosts
    @ApiMethod(name = "abcd", httpMethod = HttpMethod.GET)
	public List<Entity> abcd(User user) throws UnauthorizedException {

        //Checks if the user is registered
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Gets all post index where the user is a follower of the sender
        Query q = new Query("PostIndex").setFilter(new FilterPredicate("followers", FilterOperator.EQUAL, user.getId()));
    
        //Gets all post where the user is a follower of the sender
        return datastore.prepare(q).asList(FetchOptions.Builder.withLimit(10)).stream()
            .map(postIndex ->  {
                try {
                    Entity searchedPost = datastore.get((Key) postIndex.getProperty("post"));
                    
                    //Creates the post informations
                    Entity e = new Entity("SearchedPost");
                    e.setProperty("id", searchedPost.getKey().getId());
                    e.setProperty("sender", datastore.get(KeyFactory.createKey("User", (String) searchedPost.getProperty("sender"))).getProperty("fullName"));
                    e.setProperty("url", searchedPost.getProperty("url"));
                    e.setProperty("likes", getLikes(user, searchedPost));
                    e.setProperty("hasLiked", hasLiked(user.getId(), ((Key) postIndex.getProperty("post")).getId()));
        
                    return e;
                } catch (EntityNotFoundException | UnauthorizedException e) {
                    return null;
                }
            })
            .collect(Collectors.toList());
    }

    @ApiMethod(name = "registerUser", httpMethod = HttpMethod.POST)
	public void registerUser(User user, TinyUserInfo userInfo) throws UnauthorizedException {

        //Checks if the user is registered
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        //Creates the user
        Entity tinyUser = new Entity("User", user.getId());
        tinyUser.setProperty("fullName", userInfo.name);
        tinyUser.setProperty("name", userInfo.name.toLowerCase());
        tinyUser.setProperty("picture", userInfo.picture);
        datastore.put(tinyUser);

        //Creates the follow index
        Entity tinyFollowIndex = new Entity("FollowIndex", user.getId() + "/" + user.getId());
        tinyFollowIndex.setProperty("follower", user.getId());
        tinyFollowIndex.setProperty("followed", user.getId());
        datastore.put(tinyFollowIndex);
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

                //Creates the user informations
                Entity e = new Entity("SearchedUser");
                e.setProperty("id", searchedUser.getKey().getName());
                e.setProperty("name", searchedUser.getProperty("name"));
                e.setProperty("picture", searchedUser.getProperty("picture"));
                e.setProperty("isFollower", isFollower(user.getId(), searchedUser.getKey().getName()));

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
