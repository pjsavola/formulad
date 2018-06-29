# DefaultApi

All URIs are relative to *http://localhost:1277/v1*

Method | HTTP request | Description
------------- | ------------- | -------------
[**selectGear**](DefaultApi.md#selectGear) | **POST** /select_gear | 
[**selectMove**](DefaultApi.md#selectMove) | **POST** /select_move | 
[**startGame**](DefaultApi.md#startGame) | **POST** /initialize | 


<a name="selectGear"></a>
# **selectGear**
> Gear selectGear(gameState)



Returns gear for the next dice roll, based on the provided game state

### Example
```java
// Import classes:
//import fi.relex.model.invoker.ApiException;
//import fi.relex.model.handler.DefaultApi;


DefaultApi apiInstance = new DefaultApi();
GameState gameState = new GameState(); // GameState | Identifier of the current game and state of all players who are not finished or stopped
try {
    Gear result = apiInstance.selectGear(gameState);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling DefaultApi#selectGear");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **gameState** | [**GameState**](GameState.md)| Identifier of the current game and state of all players who are not finished or stopped |

### Return type

[**Gear**](Gear.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="selectMove"></a>
# **selectMove**
> SelectedIndex selectMove(moves)



Returns chosen move as an index to the given list of valid moves

### Example
```java
// Import classes:
//import fi.relex.model.invoker.ApiException;
//import fi.relex.model.handler.DefaultApi;


DefaultApi apiInstance = new DefaultApi();
Moves moves = new Moves(); // Moves | Identifier of the current game and all valid moves available to the player
try {
    SelectedIndex result = apiInstance.selectMove(moves);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling DefaultApi#selectMove");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **moves** | [**Moves**](Moves.md)| Identifier of the current game and all valid moves available to the player |

### Return type

[**SelectedIndex**](SelectedIndex.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="startGame"></a>
# **startGame**
> NameAtStart startGame(track)



Starts a new game with the given game identifier on the given track and assigns also player identifier

### Example
```java
// Import classes:
//import fi.relex.model.invoker.ApiException;
//import fi.relex.model.handler.DefaultApi;


DefaultApi apiInstance = new DefaultApi();
Track track = new Track(); // Track | 
try {
    NameAtStart result = apiInstance.startGame(track);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling DefaultApi#startGame");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **track** | [**Track**](Track.md)|  |

### Return type

[**NameAtStart**](NameAtStart.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

