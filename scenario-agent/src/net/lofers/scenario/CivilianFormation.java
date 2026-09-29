package net.lofers.scenario;

/** Fixed, bounded test geometry. Rows retain their spacing for every 150-tile pass. */
final class CivilianFormation {
    final int count,columns,rows;
    CivilianFormation(int count) {
        if(count!=4&&count!=32&&count!=64)throw new IllegalArgumentException("watched_actors_must_be_4_32_or_64");
        this.count=count;columns=count>=32?8:4;rows=count/columns;
    }
    float viewX(){return count==64?10616.5f:10596.5f;}
    float x(int index){check(index);return 10587.5f+index%columns;}
    float startY(int index){check(index);return 9985.5f+(index/columns-(rows-1)/2f)*1.5f;}
    float endY(int index){return startY(index)+150;}
    private void check(int i){if(i<0||i>=count)throw new IllegalArgumentException("formation_index");}
}
