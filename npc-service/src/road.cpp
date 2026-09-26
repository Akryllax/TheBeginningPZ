#include "planner.hpp"
#include <algorithm>
#include <cmath>
#include <limits>
#include <queue>
#include <set>
#include <unordered_map>

namespace npc {
namespace {
double distance(const pb::Point& a, const pb::Point& b) {
    return std::hypot(a.x() - b.x(), a.y() - b.y());
}
uint64_t cell(int x, int y) { return (uint64_t(uint32_t(x)) << 32) | uint32_t(y); }
uint64_t edge(uint32_t from, uint32_t to) { return (uint64_t(from) << 32) | to; }
}
struct RoadGraph::Data {
    std::string identity;
    std::vector<pb::RoadNode> nodes;
    std::unordered_map<uint32_t,size_t> index;
    std::unordered_map<uint64_t,std::vector<size_t>> spatial;
    std::vector<std::vector<std::pair<size_t,double>>> edges;
    double scale{1};
};
RoadGraph::RoadGraph(const pb::ObservationBatch& batch) {
    auto d=std::make_shared<Data>(); d->identity=batch.navigation_id();
    d->nodes.assign(batch.road_nodes().begin(),batch.road_nodes().end());
    d->edges.resize(d->nodes.size());
    for(size_t i=0;i<d->nodes.size();++i) {
        const auto& n=d->nodes[i];d->index.emplace(n.id(),i);
        d->spatial[cell(int(std::floor(n.position().x()/32)),int(std::floor(n.position().y()/32)))].push_back(i);
    }
    for(const auto& e:batch.road_edges()) {
        if(e.blocked())continue;
        auto a=d->index.find(e.from()),b=d->index.find(e.to());
        if(a==d->index.end()||b==d->index.end())continue;
        const double length=distance(d->nodes[a->second].position(),d->nodes[b->second].position());
        if(length>0)d->scale=std::min(d->scale,e.cost()/length);
        d->edges[a->second].emplace_back(b->second,e.cost());
    }
    for(auto& row:d->edges)std::sort(row.begin(),row.end(),[&](auto a,auto b){return d->nodes[a.first].id()<d->nodes[b.first].id();});
    data_=std::move(d);
}
const std::string& RoadGraph::identity() const { return data_->identity; }
std::shared_ptr<const RoadGraph> RoadGraphCache::get(const pb::ObservationBatch& b) {
    // Identity is provenance, not permission to ignore changed supplied graph bytes.
    pb::ObservationBatch key;key.set_navigation_id(b.navigation_id());
    *key.mutable_road_nodes()=b.road_nodes();*key.mutable_road_edges()=b.road_edges();
    std::string signature=key.SerializeAsString();
    if(!graph_||signature!=signature_) {
        graph_=std::make_shared<const RoadGraph>(key);signature_=std::move(signature);++builds_;
    }
    return graph_;
}
Route RoadGraph::route(const pb::Point& from,const pb::Point& to,const pb::ObservationBatch& batch) const {
    Route result;const auto& d=*data_;
    if(from.z()!=0||to.z()!=0){result.error="road_requires_ground_floor";return result;}
    if(d.nodes.empty()){result.error="road_graph_unavailable";return result;}
    if(batch.navigation_id()!=d.identity){result.error="navigation_identity_mismatch";return result;}
    auto nearest=[&](const pb::Point& point) {
        size_t best=d.nodes.size();double cost=std::numeric_limits<double>::infinity();
        int cx=int(std::floor(point.x()/32)),cy=int(std::floor(point.y()/32));
        // Five by five cells cover the existing 40-tile endpoint search radius.
        for(int y=cy-2;y<=cy+2;++y)for(int x=cx-2;x<=cx+2;++x) {
            auto found=d.spatial.find(cell(x,y));if(found==d.spatial.end())continue;
            for(auto i:found->second){double v=distance(point,d.nodes[i].position());
                if(v<cost||(v==cost&&(best==d.nodes.size()||d.nodes[i].id()<d.nodes[best].id()))){best=i;cost=v;}}
        }
        return std::pair(best,cost);
    };
    const auto [start,start_distance]=nearest(from);const auto [goal,goal_distance]=nearest(to);
    if(start_distance>40||goal_distance>40){result.error="road_endpoint_too_far";return result;}
    std::set<uint64_t> closed;
    for(const auto& c:batch.road_closures())if(c.expires_world_hour()>batch.world_hour())closed.insert(edge(c.from(),c.to()));
    struct Entry { double f,g;size_t at;uint32_t id; };
    auto cmp=[](const Entry& a,const Entry& b){if(a.f!=b.f)return a.f>b.f;if(a.g!=b.g)return a.g>b.g;return a.id>b.id;};
    std::priority_queue<Entry,std::vector<Entry>,decltype(cmp)> open(cmp);
    std::vector<double> scores(d.nodes.size(),std::numeric_limits<double>::infinity());
    std::vector<size_t> previous(d.nodes.size(),d.nodes.size());scores[start]=0;
    open.push({distance(d.nodes[start].position(),d.nodes[goal].position())*d.scale,0,start,d.nodes[start].id()});
    while(!open.empty()&&result.expansions<kMaxRoadExpansions) {
        const auto current=open.top();open.pop();if(current.g!=scores[current.at])continue;++result.expansions;
        if(current.at==goal){
            std::vector<size_t> path;
            for(size_t at=goal;at!=d.nodes.size();at=previous[at])path.push_back(at);
            if(path.size()>128){result.error="road_route_too_long";return result;}
            for(auto it=path.rbegin();it!=path.rend();++it){result.points.push_back(d.nodes[*it].position());result.node_ids.push_back(d.nodes[*it].id());}
            result.cost=current.g;return result;
        }
        for(auto [next,weight]:d.edges[current.at]){
            if(closed.contains(edge(d.nodes[current.at].id(),d.nodes[next].id())))continue;
            const double g=current.g+weight;if(g>=scores[next])continue;
            scores[next]=g;previous[next]=current.at;
            open.push({g+distance(d.nodes[next].position(),d.nodes[goal].position())*d.scale,g,next,d.nodes[next].id()});
        }
    }
    result.error=open.empty()?"road_disconnected":"road_search_budget";return result;
}
Route road_route(const pb::ObservationBatch& batch,const pb::Point& from,const pb::Point& to) {
    return RoadGraph(batch).route(from,to,batch);
}
}
