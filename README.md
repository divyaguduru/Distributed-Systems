Distributed System
==============
Transparent Remote File Operations
----------------------------------
An RPC system to allow remote file operations (open, read, write, ...), including a server process and a client stub library. This RPC abstraction looks very close to local file operations. To test this remote file access system, you can use existing programs ( cat, ls, ...), but interpose your RPC stubs in place of the C library functions that handle file operations. After successfully configured, you should be able to execute a command like “cat foo” but instead of opening and printing the contents of a local file, it will access the contents of file foo on the remote server machine.
> How to Run?
> ``` bash
> LD_PRELOAD=mylib.so cat foo 
> ```

File‐Caching Proxy
--------------------------
A file-cache proxy which connects the interposition library and the server mentioned above. 
The proxy will handle the RPC file operations from the client interposition library. It will fetch whole files from the server, and cache them locally. The proxy and server operate at a higher level on whole files and cover cache management operations. 
Scalable Service
-----------------------
A simulated, Cloud­hosted, multi­tier web service (a web store front).
There is a simulated cloud service with methods such as start, stop, and get status of “VMs”. It also provides a simulated load balancer – once servers register with this, it will deliver client requests to all of the servers in a round­robin fashion (i.e., a request is given to the next server in line, after which the server moves to the end of the line).
The web service is a simulated online store. Requests can be either a browse request (to provide information on items and categories of items), or a purchase request to buy an item. 
Two‐phase Commit Service
--------------------------------
A system that generates and publishes group collages assembled from multiple images contributed by multiple individuals. The system consists of several UserNodes, each of which represents a single person’s smartphone or laptop, and a single Server, which coordinates the publication of collages. The process for publishing a collage follows these steps:
> 1. Someone constructs a candidate collage made from images shared by the UserNodes, and posts it to the Server.
> 2. The Server initiates a two-­phase commit procedure, letting the users that contributed images to the collage examine it to see if they are happy with the result.
> 3. UserNodes either approve or disapprove it.
> 4. Only if all UserNodes that contribute an image to the collage approve, the collage is published (written to the Server’s working directory).
> 5. Due to licensing terms on the published collages, a UserNode needs to ensure that any of its source images appear in no more than one committed / published collage. Furthermore, the UserNode is required to stop sharing (i.e., remove from its working directory) any of its images that appear in a published collage.

This system is robust enough to handle lost messages and to node failures / reboots.

