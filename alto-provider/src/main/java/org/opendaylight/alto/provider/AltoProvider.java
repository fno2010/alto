package org.opendaylight.alto.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.opendaylight.controller.config.yang.config.alto_provider.impl.AltoProviderRuntimeMXBean;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.AltoServiceService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.EndpointCostServiceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.EndpointCostServiceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.EndpointCostServiceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.FilteredCostMapServiceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.FilteredCostMapServiceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.FilteredNetworkMapServiceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.FilteredNetworkMapServiceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.Resources;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.input.CostType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.input.Endpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.EndpointCostService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.EndpointCostServiceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.EndpointCostMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.EndpointCostMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.EndpointCostMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.Meta;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.MetaBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.endpoint.cost.map.DstCosts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.endpoint.cost.map.DstCostsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.types.rev150404.Constraint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.types.rev150404.CostMetric;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.types.rev150404.CostMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.types.rev150404.TypedEndpointAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;////////////////////////////////////
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.host.AttachmentPoints;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.costdefault.rev150507.DstCosts1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.costdefault.rev150507.DstCosts1Builder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;

public class AltoProvider implements
            AltoServiceService, DataChangeListener,
            AltoProviderRuntimeMXBean, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AltoProvider.class);

    private ListenerRegistration<DataChangeListener> hostNodeListerRegistration;
    
    private ListenerRegistration<DataChangeListener> linkListerRegistration;
    private Map<String, String> ipSwitchIdMap = null;
    Graph<NodeId, Link> networkGraph = null;
    Set<String> linkAdded = new HashSet<>();
    DijkstraShortestPath<NodeId, Link> shortestPath = null;
	
    public static final InstanceIdentifier<Resources> ALTO_IID
                        = InstanceIdentifier.builder(Resources.class).build();

    private DataBroker dataProvider;
    private final ExecutorService executor;

    public AltoProvider() {
    	this.ipSwitchIdMap = new HashMap<String, String>();
        this.executor = Executors.newFixedThreadPool(1);
        //////////////////////////////////////////////////////////////
    }

    public void setDataProvider(final DataBroker salDataProvider) {
        this.dataProvider = salDataProvider;
        log.info(this.getClass().getName() + " data provider initiated");
    }

    public void registerAsDataChangeListener() {
		InstanceIdentifier<HostNode> hostNodes = InstanceIdentifier
				.builder(NetworkTopology.class)//
				.child(Topology.class,
						new TopologyKey(new TopologyId("flow:1")))//
				.child(Node.class).augmentation(HostNode.class).build();
		this.hostNodeListerRegistration = dataProvider
				.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
						hostNodes, this, DataChangeScope.BASE);

		InstanceIdentifier<Link> links = InstanceIdentifier
				.builder(NetworkTopology.class)//
				.child(Topology.class,
						new TopologyKey(new TopologyId("flow:1")))//
				.child(Link.class).build();
		this.linkListerRegistration = dataProvider.registerDataChangeListener(
				LogicalDatastoreType.OPERATIONAL, links, this,
				DataChangeScope.BASE);

		InstanceIdentifier<Topology> topology = InstanceIdentifier
				.builder(NetworkTopology.class)//
				.child(Topology.class,
						new TopologyKey(new TopologyId("flow:1"))).build();

		ReadOnlyTransaction newReadOnlyTransaction = dataProvider
				.newReadOnlyTransaction();

		ListenableFuture<Optional<Topology>> dataFuture = newReadOnlyTransaction
				.read(LogicalDatastoreType.OPERATIONAL, topology);
		try {
			Topology get = dataFuture.get().get();
			//log.trace("test " + get);
		} catch (InterruptedException | ExecutionException ex) {
			java.util.logging.Logger.getLogger(AltoProvider.class.getName())
					.log(Level.SEVERE, null, ex);
		}
		Futures.addCallback(dataFuture,
				new FutureCallback<Optional<Topology>>() {
					@Override
					public void onSuccess(final Optional<Topology> result) {
						if (result.isPresent()) {
							log.trace("Processing NEW NODE? " + result.get());
							processTopology(result.get());
						}
					}

					@Override
					public void onFailure(Throwable arg0) {
					}
				});
	}
    
    public synchronized void addLinks(List<Link> links) {
		if (links == null || links.isEmpty()) {
			log.info("In addLinks: No link added as links is null or empty.");
			return;
		}

		if (networkGraph == null) {
			networkGraph = new SparseMultigraph<>();
		}

		for (Link link : links) {
			if (linkAlreadyAdded(link)) {
				continue;
			}
			NodeId sourceNodeId = link.getSource().getSourceNode();
			NodeId destinationNodeId = link.getDestination().getDestNode();
			networkGraph.addVertex(sourceNodeId);
			networkGraph.addVertex(destinationNodeId);
			networkGraph.addEdge(link, sourceNodeId, destinationNodeId,
					EdgeType.UNDIRECTED);
		}

		if (shortestPath == null) {
			shortestPath = new DijkstraShortestPath<>(networkGraph);
		} else {
			shortestPath.reset();
		}
	}

	private boolean linkAlreadyAdded(Link link) {
		String linkAddedKey = null;
		if (link.getDestination().getDestTp().hashCode() > link.getSource()
				.getSourceTp().hashCode()) {
			linkAddedKey = link.getSource().getSourceTp().getValue()
					+ link.getDestination().getDestTp().getValue();
		} else {
			linkAddedKey = link.getDestination().getDestTp().getValue()
					+ link.getSource().getSourceTp().getValue();
		}
		if (linkAdded.contains(linkAddedKey)) {
			return true;
		} else {
			linkAdded.add(linkAddedKey);
			return false;
		}
	}

	public void processTopology(Topology topology) {
		List<Node> nodeList = topology.getNode();
		for (int i = 0; i < nodeList.size(); ++i) {
			Node node = nodeList.get(i);
			HostNode hostNode = node.getAugmentation(HostNode.class);
			log.info("process node "+i+hostNode);
			processNode(hostNode);
		}
		List<Link> linkList = topology.getLink();
		addLinks(linkList);
	}

	private void deleteHostNode(HostNode hostNode) {
		List<AttachmentPoints> attachmentPoints = hostNode
				.getAttachmentPoints();

		TpId tpId = attachmentPoints.get(0).getTpId();
		String tpIdString = tpId.getValue();

		String ipv4String = hostNode.getAddresses().get(0).getIp()
				.getIpv4Address().getValue();

		this.ipSwitchIdMap.remove(ipv4String);
	}

	private void processNode(HostNode hostNode) {
		if(hostNode==null)return;
		List<AttachmentPoints> attachmentPoints = hostNode
				.getAttachmentPoints();

		TpId tpId = attachmentPoints.get(0).getTpId();
		String tpIdString = tpId.getValue();

		String ipv4String = hostNode.getAddresses().get(0).getIp()
				.getIpv4Address().getValue();

		this.ipSwitchIdMap.put(ipv4String, tpIdString);
	}
	

    @Override
    public void onDataChanged(
            final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
//        DataObject dataObject = change.getUpdatedSubtree();
//        if (dataObject instanceof Resources) {
//            Resources altoResources = (Resources) dataObject;
//            log.info("onDataChanged - new ALTO config: {}", altoResources);
                log.info("in Data Changed");
		if (change == null) {
			log.info("In onDataChanged: No processing done as change even is null.");
			return;
		}
		Map<InstanceIdentifier<?>, DataObject> updatedData = change
				.getUpdatedData();
		Map<InstanceIdentifier<?>, DataObject> createdData = change
				.getCreatedData();
		Map<InstanceIdentifier<?>, DataObject> originalData = change
				.getOriginalData();
		Set<InstanceIdentifier<?>> deletedData = change.getRemovedPaths();

		for (InstanceIdentifier<?> iid : deletedData) {
                        log.info("delete Data");
			if (iid.getTargetType().equals(HostNode.class)) {
                                log.info("delete hostnode");
				HostNode node = ((HostNode) originalData.get(iid));
				deleteHostNode(node);
			} else if (iid.getTargetType().equals(Link.class)) {
				// TODO performance improvement here
                log.info("delete edge");
		                String linkAddedKey = null;
                                Link link = (Link) originalData.get(iid);
		                if (link.getDestination().getDestTp().hashCode() > link.getSource()
				        .getSourceTp().hashCode()) {
			            linkAddedKey = link.getSource().getSourceTp().getValue()
					+ link.getDestination().getDestTp().getValue();
		                } else {
			            linkAddedKey = link.getDestination().getDestTp().getValue()
					+ link.getSource().getSourceTp().getValue();
		                }
		                if (linkAdded.contains(linkAddedKey)) {
		                	linkAdded.remove(linkAddedKey);
		                }
				networkGraph.removeEdge((Link) originalData.get(iid));
                shortestPath.reset();
                shortestPath = new DijkstraShortestPath<>(networkGraph);
			}
		}

		for (Map.Entry<InstanceIdentifier<?>, DataObject> entrySet : updatedData
				.entrySet()) {
                        log.info("update hostnode data");
			InstanceIdentifier<?> iiD = entrySet.getKey();
			final DataObject dataObject = entrySet.getValue();
			if (dataObject instanceof HostNode) {
				processNode((HostNode) dataObject);
			}
		}

		for (Map.Entry<InstanceIdentifier<?>, DataObject> entrySet : createdData
				.entrySet()) {
			InstanceIdentifier<?> iiD = entrySet.getKey();
			final DataObject dataObject = entrySet.getValue();
			if (dataObject instanceof HostNode) {
                                log.info("update HostNode");
				processNode((HostNode) dataObject);
			} else if (dataObject instanceof Link) {
                                log.info("update link");
				Link link = (Link) dataObject;
				if (!linkAlreadyAdded(link)) {
					NodeId sourceNodeId = link.getSource().getSourceNode();
					NodeId destinationNodeId = link.getDestination()
							.getDestNode();
					networkGraph.addVertex(sourceNodeId);
					networkGraph.addVertex(destinationNodeId);
					networkGraph.addEdge(link, sourceNodeId, destinationNodeId,
							EdgeType.UNDIRECTED);
                    log.info("update link in networkGraph");
                    shortestPath.reset();
                    shortestPath = new DijkstraShortestPath<>(networkGraph);
				}
			}
		}
           
        
    }

    public List<EndpointCostMap> hopcountNumerical(List<TypedEndpointAddress> srcs, List<TypedEndpointAddress> dsts) {

		List<EndpointCostMap> result = new ArrayList<EndpointCostMap>();
		for (int i = 0; i < srcs.size(); ++i) {
			TypedEndpointAddress teaSrc = srcs.get(i);
			String ipv4SrcString = teaSrc.getTypedIpv4Address().getValue()
					.substring(5);
                        log.info("ipv4SrcString:"+ipv4SrcString);
			String tpIdSrc = this.ipSwitchIdMap.get(ipv4SrcString);
			String[] tempi = tpIdSrc.split(":");
			String swSrcId = tempi[0] + ":" + tempi[1];
                        log.info("swSrcId:"+swSrcId);
			List<DstCosts> dstCostsList = new ArrayList<DstCosts>();

			for (int j = 0; j < dsts.size(); ++j) {
				TypedEndpointAddress teaDst = dsts.get(j);
				String ipv4DstString = teaDst.getTypedIpv4Address().getValue()
						.substring(5);
				String tpIdDst = this.ipSwitchIdMap.get(ipv4DstString);
				String[] tempj = tpIdDst.split(":");
				String swDstId = tempj[0] + ":" + tempj[1];

				NodeId srcNodeId = new NodeId(swSrcId);
				NodeId dstNodeId = new NodeId(swDstId);
                                log.info("caculate shortest path");
				Number number = shortestPath.getDistance(srcNodeId, dstNodeId);
                                DstCosts1 dst1 = null;
                                if (number!=null) {
				    dst1 = new DstCosts1Builder().setCostDefault(number.intValue()).build();
                                }
                                else {
                                    dst1 = new DstCosts1Builder().setCostDefault(Integer.MAX_VALUE).build();
                                }
				DstCosts dstCost = new DstCostsBuilder()
						.addAugmentation(DstCosts1.class, dst1).setDst(teaDst)
						.build();
				dstCostsList.add(dstCost);
			}
			EndpointCostMap ecp = new EndpointCostMapBuilder().setSrc(teaSrc)
					.setDstCosts(dstCostsList)
					.setKey(new EndpointCostMapKey(teaSrc)).build();
			result.add(ecp);
		}
		return result;
	}
    
    @Override
    public Future<RpcResult<EndpointCostServiceOutput>> endpointCostService(
            EndpointCostServiceInput input) {
/*        // TODO Auto-generated method stub
    	
        return null;*/
        log.info("all input:"+input);
    	log.info("start rpc");
        CostType costTypeInput = null;
        List<Constraint> constraintsInput = null;
        Endpoints endpointsInput = null;
    	
        RpcResultBuilder<EndpointCostServiceOutput> endpointCostServiceBuilder = null;

        EndpointCostServiceOutput output = null;
        //EndpointCostServiceOutputBuilder outputBuilder = null;
        
        if ((costTypeInput = input.getCostType()) == null) {
            endpointCostServiceBuilder = RpcResultBuilder.<EndpointCostServiceOutput>failed().withError(ErrorType.APPLICATION, "Invalid cost-type value ", "Argument can not be null.");
        }
        else {
            log.info("costTypeInput: "+costTypeInput);
            if ((endpointsInput = input.getEndpoints()) == null) {
                endpointCostServiceBuilder = RpcResultBuilder.<EndpointCostServiceOutput>failed().withError(ErrorType.APPLICATION, "Invalid endpoints value ", "Argument can not be null.");
            }
            else {
                log.info("costMetric: "+costTypeInput.getCostMetric());
            	Endpoints endpoints = input.getEndpoints();
            	List<TypedEndpointAddress> srcs = endpoints.getSrcs();
            	List<TypedEndpointAddress> dsts = endpoints.getDsts();
            	//judge whether srcs or dsts are discovered
                boolean srcDstFoundFlag = true;
            	for (int i = 0; i < srcs.size(); ++i) {
            		TypedEndpointAddress teaSrc = srcs.get(i);
        			String ipv4SrcString = teaSrc.getTypedIpv4Address().getValue()
        					.substring(5);
                                log.info("parsed ipv4SrcString:"+ipv4SrcString);
                    if (this.ipSwitchIdMap.get(ipv4SrcString) == null) {
                    	endpointCostServiceBuilder = RpcResultBuilder.<EndpointCostServiceOutput>failed().withError(ErrorType.APPLICATION, "Invalid endpoints value ", "IP can not be found.");
                        srcDstFoundFlag = false;
                    }           		
            	}
            	for (int j = 0; j < dsts.size(); ++j) {
    				TypedEndpointAddress teaDst = dsts.get(j);
    				String ipv4DstString = teaDst.getTypedIpv4Address().getValue()
    						.substring(5);
                                log.info("parsed ipv4DstString:"+ipv4DstString);
    				if (this.ipSwitchIdMap.get(ipv4DstString) == null) {
    					endpointCostServiceBuilder = RpcResultBuilder.<EndpointCostServiceOutput>failed().withError(ErrorType.APPLICATION, "Invalid endpoints value ", "IP can not be found.");
                        srcDstFoundFlag = false;
    				}
            	}
                CostMetric costMetric = costTypeInput.getCostMetric();
                CostMode costMode = costTypeInput.getCostMode();
                log.info("costmetric string:888"+costMetric.getString());
                log.info("costmode string:888"+costMode);
                if (srcDstFoundFlag && costMode.equals(CostMode.Numerical) && (costMetric.getEnumeration() == CostMetric.Enumeration.Hopcount || costMetric.getString().equals("hopcount"))){
                	log.info("in hopcount");
                	org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.meta.CostType costType = new org.opendaylight.yang.gen.v1.urn.opendaylight.alto.rev150404.endpoint.cost.service.output.endpoint.cost.service.meta.CostTypeBuilder()
                	.setCostMetric(costMetric)
                	.setCostMode(costMode).build();
                	Meta meta = new MetaBuilder().setCostType(costType).build();
                	List<EndpointCostMap> ecmList = hopcountNumerical(srcs, dsts);
                	EndpointCostService ecs = new EndpointCostServiceBuilder().setMeta(meta).setEndpointCostMap(ecmList).build();
                	
                    if ((output = new EndpointCostServiceOutputBuilder().setEndpointCostService(ecs).build()) != null) {
                        log.info("output valid");
                        endpointCostServiceBuilder = RpcResultBuilder.success(output);
                    }
                    else {
                        endpointCostServiceBuilder = RpcResultBuilder.<EndpointCostServiceOutput>failed().withError(ErrorType.APPLICATION, "Invalid output value", "Output is null.");
                    }
                    return Futures.immediateFuture(endpointCostServiceBuilder.build());
                
                }
                
            }
        }
        return Futures.immediateFuture(RpcResultBuilder.<EndpointCostServiceOutput>failed().withError(ErrorType.APPLICATION, "Invalid output value", "Output is null.").build());
  
    }

    @Override
    public Future<RpcResult<FilteredCostMapServiceOutput>> filteredCostMapService(
            FilteredCostMapServiceInput input) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Future<RpcResult<FilteredNetworkMapServiceOutput>> filteredNetworkMapService(
            final FilteredNetworkMapServiceInput input) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() throws ExecutionException, InterruptedException {
    	this.hostNodeListerRegistration.close();
    	this.linkListerRegistration.close();
		this.ipSwitchIdMap.clear();
    	executor.shutdown();
        if (dataProvider != null) {
            WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
            tx.delete(LogicalDatastoreType.CONFIGURATION, ALTO_IID);
            Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
                    log.debug("Delete ALTO commit result: " + result);
                }

            @Override
            public void onFailure(final Throwable t) {
                log.error("Delete of ALTO failed", t);
            }
            });
        }
    }
}
