# Tinygram - 2022

BOUREFIS Houda - GERARD Kylian

# Website:

https://wcd-cloud-datastore-projet.ew.r.appspot.com

# Kinds:

- User:

Contains the id of the user, his name and the url of his profile picture.

![alt text](https://github.com/Bobinho44/-PROJET_WCD_M1/blob/master/resources/User.png?raw=true)

- Post:

Contains a follower list, a url, the name of the sender and a global like counter. We didn't use the post index method, because the content of the post is only composed of a string. Using index posts with the keys to the post made the creation of posts slower.


![alt text](https://github.com/Bobinho44/-PROJET_WCD_M1/blob/master/resources/Post.png?raw=true)

- LikeIndex:

Contains the id of the person who liked, the key of the associated post and the date of the post (to allow to retrieve only the posts liked among the n-last ones).

![alt text](https://github.com/Bobinho44/-PROJET_WCD_M1/blob/master/resources/LikeIndex.png?raw=true)

- LikeCounter:
 
Contains the key of the associated post and a like counter. These counters are sub-counters, which means that several like counters can be linked to the same post.

![alt text](https://github.com/Bobinho44/-PROJET_WCD_M1/blob/master/resources/LikeCounter.png?raw=true)

- FollowIndex:
 
Contains the id of the follower and the person followed.

![alt text](https://github.com/Bobinho44/-PROJET_WCD_M1/blob/master/resources/FollowIndex.png?raw=true)

# How much time (ms) does it take to post of message?


|            | 10 followers  | 100 followers | 500 followers |
|------------|---------------|---------------|---------------|
| 1 | 132      |      137      |      277      |
| 2	| 85 |	191 |	266 |
| 3	| 76	| 113	|301 | 
| 4	| 83	| 102	|275 | 
| 5	| 153	 |189	|238 |
| 6	| 95	| 130	|253 |
| 7	| 166	| 106 |	254 |
| 8	| 95	| 113	| 316 |
| 9	| 110	| 124	| 300 |
| 10 | 85	| 117	| 272 |
| 11	| 85	| 171	| 244 |
| 12	| 88	| 107	| 281|
| 13	| 91	| 164	| 262|
| 14	| 138 |	111	| 315|
| 15	| 83	| 114	| 261|
| 16	| 91	| 143	| 326|
| 17	| 81	| 118	| 289|
| 18	| 101	| 136	| 378|
| 19	| 94	| 97	| 268|
| 20	| 105	| 127	| 274|
| 21	| 127	| 105	| 190|
| 22	| 77 |	93	| 236|
| 23	| 80	| 109	| 303|
| 24	| 101	| 149	| 286|
| 25	| 130	| 114	| 265|
| 26	| 105	| 117	| 270|
| 27	| 107	| 120	| 263|
| 28	| 96	| 121	| 242|
| 29	| 91	| 113	| 180|
| 30	| 92	| 91	| 177|
| Average	|101,4 |	124,7 |	268,7 |

# How much time (ms) does it take to retrieve last messages?


|            | 10 posts  | 100 posts | 500 posts |
|------------|---------------|---------------|---------------|
|1|	53|	357|	1414|
|2	|80	|311	|1462|
|3	|62	|382	|1299|
|4	|84	|369	|1393|
|5	|96	|347	|1279|
|6	|75	|390	|1395|
|7	|66	|324	|1167|
|8	|72	|381	|1390|
|9	|78	|383	|1235|
|10	|54	|323	|1374|
|11	|61	|390	|1236|
|12	|76	|386	|1214|
|13	|64	|345	|1276|
|14	|77	|384	|1374|
|15	|54	|329	|1210|
|16	|73	|386	|1415|
|17	|67	|384	|1241|
|18	|55	|368	|1317|
|19	|66	|344	|1378|
|20	|55	|396	|1170|
|21	|77	|331	|1368|
|22	|67	|357	|1322|
|23	|81	|365	|1351|
|24	|77	|398	|1417|
|25	|66	|339	|1345|
|26	|65	|387	|1265|
|27	|71	|359	|1430|
|28	|59	|367	|1432|
|29	|71	|354	|1385|
|30	|45	|362	|1274|
|Average	|68,2	|363,3	|1327,6|

# Conclusion

It can be noticed that the computation time does not increase linearly with the size of the data. We can therefore deduce that the application scales. However, it does not scale enough (otherwise we would obtain globally constant times). 

For the recovery of the last posts, the scalability problem is due to the operations performed on each post to manage the likes. To improve this, we could try to better store the likes, to determine if a user has already liked a post, how many likes the post has...  We tried to update a global counter of the post (by summing the sub-counters) after a user has liked it. This technique has its limits, because although the right number of likes is stored correctly in the sub-counters, the display may not be up to date (depending on which user is doing the global update in case of competition).
To determine if the player has already liked a post, we retrieve from the n-last posts that are the ones with a like index between the user and the post.

For the post creation part, we could perhaps modify the way the follows are stored. Instead of creating one line per follower-followed link, we could store all this in a list (which would reduce the number of lines to retrieve). However, lists are not really adapted to updating (you have to get the whole list, modify it, and put it in the datastore).
