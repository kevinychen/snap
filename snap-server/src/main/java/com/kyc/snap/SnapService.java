package com.kyc.snap;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/")
public interface SnapService {

    @GET
    @Path("productName")
    String getProductName();

    @POST
    @Path("gridify")
    Grid gridify(GridifyRequest request);

    @POST
    @Path("parseGrid")
    ParsedGrid parseGrid(ParseGridRequest request);
}
