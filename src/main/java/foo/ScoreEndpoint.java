package foo;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.config.Nullable;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.api.server.spi.auth.EspAuthenticator;

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
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.QueryResultList;
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

    @ApiMethod(name = "registerUser", httpMethod = HttpMethod.POST)
	public void registerUser(User user, TinyUserInfo userInfo) throws UnauthorizedException {
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
        Entity tinyUser2 = new Entity("User", "abcdefg9999");
        tinyUser.setProperty("fullName", "Jean Benoit");
        tinyUser2.setProperty("name", "jean benoit");
        tinyUser2.setProperty("picture", "https://cdn.pixabay.com/photo/2015/04/23/22/00/tree-736885__480.jpg");
        datastore.put(tinyUser2);

        //Creates the follow index
        Entity tinyFollowIndex = new Entity("FollowIndex", user.getId() + "/" + user.getId());
        tinyFollowIndex.setProperty("follower", user.getId());
        tinyFollowIndex.setProperty("followed", user.getId());
        datastore.put(tinyFollowIndex);
    }

    @ApiMethod(name = "searchUsers", httpMethod = HttpMethod.GET)
	public List<Entity> searchUsers(User user, TinySearchUserInfo searchUserInfo) throws UnauthorizedException {
		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
        }

        if (searchUserInfo.name.equals("")) {
            return Collections.emptyList();
        }
        
		Query q = new Query("User").setFilter(new FilterPredicate("name", FilterOperator.GREATER_THAN_OR_EQUAL, searchUserInfo.name));

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		
        return pq.asList(FetchOptions.Builder.withLimit(10)).stream().map(searchedUser -> {
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

        try {
            datastore.get(KeyFactory.createKey("FollowIndex", follower + "/" + followed));
            return true;
        }
        catch (EntityNotFoundException e) {
            return false;
        }
    }
	Random r = new Random();

    // remember: return Primitives and enums are not allowed. 
	@ApiMethod(name = "getRandom", httpMethod = HttpMethod.GET)
	public RandomResult random() {
		return new RandomResult(r.nextInt(6) + 1);
	}

	@ApiMethod(name = "hello", httpMethod = HttpMethod.GET)
	public User Hello(User user) throws UnauthorizedException {
        if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}
        System.out.println("Yeah:"+user.toString());
		return user;
	}


	@ApiMethod(name = "scores", httpMethod = HttpMethod.GET)
	public List<Entity> scores() {
		Query q = new Query("Score").addSort("score", SortDirection.DESCENDING);

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withLimit(100));
		return result;
	}

	@ApiMethod(name = "myscores", httpMethod = HttpMethod.GET)
	public List<Entity> myscores(@Named("name") String name) {
		Query q = new Query("Score").setFilter(new FilterPredicate("name", FilterOperator.EQUAL, name)).addSort("score",
				SortDirection.DESCENDING);

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);
		List<Entity> result = pq.asList(FetchOptions.Builder.withLimit(10));
		return result;
	}

	@ApiMethod(name = "addScore", httpMethod = HttpMethod.GET)
	public Entity addScore(@Named("score") int score, @Named("name") String name) {

		Entity e = new Entity("Score", "" + name + score);
		e.setProperty("name", name);
		e.setProperty("score", score);

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		datastore.put(e);

		return e;
	}

	@ApiMethod(name = "postMessage", httpMethod = HttpMethod.POST)
	public Entity postMessage(PostMessage pm) {

		Entity e = new Entity("Post"); // quelle est la clef ?? non specifié -> clef automatique
		e.setProperty("owner", pm.owner);
		e.setProperty("url", pm.url);
		e.setProperty("body", pm.body);
		e.setProperty("likec", 0);
		e.setProperty("date", new Date());

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Transaction txn = datastore.beginTransaction();
		datastore.put(e);
		txn.commit();
		return e;
	}

	@ApiMethod(name = "mypost", httpMethod = HttpMethod.GET)
	public CollectionResponse<Entity> mypost(@Named("name") String name, @Nullable @Named("next") String cursorString) {

	    Query q = new Query("Post").setFilter(new FilterPredicate("owner", FilterOperator.EQUAL, name));

	    // https://cloud.google.com/appengine/docs/standard/python/datastore/projectionqueries#Indexes_for_projections
	    //q.addProjection(new PropertyProjection("body", String.class));
	    //q.addProjection(new PropertyProjection("date", java.util.Date.class));
	    //q.addProjection(new PropertyProjection("likec", Integer.class));
	    //q.addProjection(new PropertyProjection("url", String.class));

	    // looks like a good idea but...
	    // generate a DataStoreNeedIndexException -> 
	    // require compositeIndex on owner + date
	    // Explosion combinatoire.
	    // q.addSort("date", SortDirection.DESCENDING);
	    
	    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	    PreparedQuery pq = datastore.prepare(q);
	    
	    FetchOptions fetchOptions = FetchOptions.Builder.withLimit(2);
	    
	    if (cursorString != null) {
		fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
		}
	    
	    QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);
	    cursorString = results.getCursor().toWebSafeString();
	    
	    return CollectionResponse.<Entity>builder().setItems(results).setNextPageToken(cursorString).build();
	    
	}
    
	@ApiMethod(name = "getPost",
		   httpMethod = ApiMethod.HttpMethod.GET)
	public CollectionResponse<Entity> getPost(User user, @Nullable @Named("next") String cursorString)
			throws UnauthorizedException {

		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}

		Query q = new Query("Post").
		    setFilter(new FilterPredicate("owner", FilterOperator.EQUAL, user.getEmail()));

		// Multiple projection require a composite index
		// owner is automatically projected...
		// q.addProjection(new PropertyProjection("body", String.class));
		// q.addProjection(new PropertyProjection("date", java.util.Date.class));
		// q.addProjection(new PropertyProjection("likec", Integer.class));
		// q.addProjection(new PropertyProjection("url", String.class));

		// looks like a good idea but...
		// require a composite index
		// - kind: Post
		//  properties:
		//  - name: owner
		//  - name: date
		//    direction: desc

		// q.addSort("date", SortDirection.DESCENDING);

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		PreparedQuery pq = datastore.prepare(q);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(2);

		if (cursorString != null) {
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
		}

		QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);
		cursorString = results.getCursor().toWebSafeString();

		return CollectionResponse.<Entity>builder().setItems(results).setNextPageToken(cursorString).build();
	}

	@ApiMethod(name = "postMsg", httpMethod = HttpMethod.POST)
	public Entity postMsg(User user, PostMessage pm) throws UnauthorizedException {

		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}

		Entity e = new Entity("Post", Long.MAX_VALUE-(new Date()).getTime()+":"+user.getEmail());
		e.setProperty("owner", user.getEmail());
		e.setProperty("url", pm.url);
		e.setProperty("body", pm.body);
		e.setProperty("likec", 0);
		e.setProperty("date", new Date());

///		Solution pour pas projeter les listes
//		Entity pi = new Entity("PostIndex", e.getKey());
//		HashSet<String> rec=new HashSet<String>();
//		pi.setProperty("receivers",rec);
		
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Transaction txn = datastore.beginTransaction();
		datastore.put(e);
//		datastore.put(pi);
		txn.commit();
		return e;
	}
}
