Notices will go here:

So if any thread safty problems come up in the future change the creation of the:
```
        Thread vThread = Thread.ofVirtual().start(() -> {
            while(true){
                worldAccess.processPendingBlockChanges();
                try {
                    Thread.sleep(200);
                } catch (Exception e) {}
            }
        });
```
  in init at main on startup. OR go to the end of the process pending changes and make it all done on main thread.

Something is wrong with the 