
# PlayerState

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**playerId** | **String** |  | 
**gear** | **Integer** |  | 
**type** | [**TypeEnum**](#TypeEnum) |  | 
**nodeId** | **Integer** |  | 
**hitpoints** | **Integer** | Remaining hitpoints of the player. | 
**stops** | **Integer** | The amount of stops the player has made in the current curve or zero. | 
**leeway** | **Integer** | The number of milliseconds the player can miss the time limits for the rest of the game. | 


<a name="TypeEnum"></a>
## Enum: TypeEnum
Name | Value
---- | -----
START | &quot;START&quot;
STRAIGHT | &quot;STRAIGHT&quot;
CURVE_1 | &quot;CURVE_1&quot;
CURVE_2 | &quot;CURVE_2&quot;
CURVE_3 | &quot;CURVE_3&quot;
FINISH | &quot;FINISH&quot;



