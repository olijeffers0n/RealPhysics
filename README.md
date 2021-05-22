![GIF](https://media3.giphy.com/media/yDqbkfwQTFJS9M9PI7/giphy.gif?cid=790b7611eb3752c5e260eba52da4a7df15d800594f0f54f0&rid=giphy.gif&ct=g)

# RealPhysics!

This is a plugin designed to replicate realistic physics on explosions using `FallingBlocks` in order to create the entities.

This plugin depends on ProtocolLib in order to work, as it uses the packets in order to stop the block despawing on contact with the ground. 

## Bugs!
Multi-Explosion explosions are a bit choppy and laggy, this is due to an extra packet the server sends that i am having a hard time weeding out. Please, help me fix it!

## Blocked Blocks!
Some Blocks just do not work when they are exploded, and i have included a few:
```
- TNT  
- SNOW  
- GRASS  
- TALL_GRASS  
- DEAD_BUSH  
- FERN  
- LARGE_FERN  
- DARK_OAK_DOOR  
- OAK_DOOR  
- BIRCH_DOOR  
- SPRUCE_DOOR  
- IRON_DOOR  
- ACACIA_DOOR  
- JUNGLE_DOOR  
- SPRUCE_DOOR  
- CRIMSON_DOOR  
- WARPED_DOOR
- FIRE
```
There are some more, so just add them to the file using the names from [here](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html)

## Delay
The plugin uses a bell curve to distribute the despawning, the max and min times can be changed in the `config.yml` in the unit of ticks


## Vector
The Vector is hard - coded into the program, however it definitely is not perfect. Feel free to open a PR with any changes! 

### Summary

Any changes, just make a PR or contact me on discord at `Ollie#0175`

:-)
