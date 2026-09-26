import * as T from 'three';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
import type { Element } from './types';

export const palette: Record<string, string> = {
  floor:'#708570',wall:'#e0d7bf',door:'#997857',window:'#8cb3ac',roof:'#627c73',stairs:'#b5ab95',
  fence:'#b7a486',tree:'#557757',vegetation:'#78955b',furniture:'#c6a783',container:'#ac9574',
  vehicle:'#d5a257',zombie:'#ab7970',animal:'#b7a886',item:'#c9bc88',unknown:'#b393b4',
};

export function templateKey(o: Element): string {
  if (o.kind !== 'furniture' && o.kind !== 'container') return o.kind;
  const name=(o.state.variant+' '+o.label+' '+o.sprite).toLowerCase();
  for (const kind of ['bed','chair','table','shelf','counter','cabinet','sofa','fridge','sink','toilet']) {
    if(name.includes(kind)) return kind;
  }
  return o.kind;
}

export function template(kind: string): T.BufferGeometry {
  const parts: T.BufferGeometry[]=[];
  function add(g:T.BufferGeometry,x:number,y:number,z:number,color='#ffffff') {
    g.translate(x,y,z);
    const count=g.getAttribute('position').count,c=new T.Color(color),colors=new Float32Array(count*3);
    for(let i=0;i<count;i++) c.toArray(colors,i*3);
    g.setAttribute('color',new T.BufferAttribute(colors,3));
    parts.push(g);
  }
  const box=(w:number,h:number,d:number,x=0,y=h/2,z=0,c='#ffffff')=>add(new T.BoxGeometry(w,h,d),x,y,z,c);
  const cylinder=(rt:number,rb:number,h:number,x:number,y:number,z:number,c='#ffffff')=>add(new T.CylinderGeometry(rt,rb,h,7),x,y,z,c);
  switch(kind) {
    case 'floor': box(1,.055,1,0,-.035); break;
    case 'wall': box(1,1,.09); break;
    case 'door': box(.82,.96,.1,0,.48); box(.08,.08,.14,.29,.46,0,'#d6b473'); break;
    case 'window':
      box(1,.33,.09,0,.165);box(1,.17,.09,0,.915);box(.12,.5,.09,-.44,.58);box(.12,.5,.09,.44,.58);
      box(.74,.42,.035,0,.58,0,'#659fba'); break;
    case 'roof': box(1,.08,1,0,1); break;
    case 'stairs': for(let i=0;i<6;i++)box(1,(i+1)/6,1/6,0,(i+1)/12,(i+.5)/6-.5);break;
    case 'fence':
      for(let i=0;i<5;i++)box(.09,1,.09,i/4-.5,.5,0);
      box(1,.1,.08,0,.25);box(1,.1,.08,0,.78);break;
    case 'tree': cylinder(.06,.09,.4,0,.2,0,'#796c56');cylinder(.05,.48,.72,0,.64,0);break;
    case 'vegetation': cylinder(.13,.4,.5,0,.25,0);break;
    case 'vehicle':
      box(.92,.48,.96,0,.39);box(.78,.42,.55,0,.76,-.1,'#d8ddd3');
      box(.81,.2,.025,0,.79,-.39,'#42646b');box(.81,.2,.025,0,.79,.19,'#42646b');
      for(const x of [-.44,.44])for(const z of [-.3,.3])box(.16,.3,.18,x,.19,z,'#34413d');
      box(.6,.08,.02,0,.4,-.495,'#e8e5b8');break;
    case 'bed': box(.95,.28,.95,0,.2);box(.9,.22,.83,0,.45,.04,'#d1d3c1');box(.92,.18,.19,0,.59,-.31,'#eff0de');break;
    case 'chair':
      box(.8,.12,.8,0,.52);box(.8,.48,.12,0,.76,.34);
      for(const x of [-.29,.29])for(const z of [-.29,.29])box(.09,.5,.09,x,.25,z);break;
    case 'table': box(1,.14,1,0,.87);for(const x of [-.4,.4])for(const z of [-.4,.4])box(.1,.8,.1,x,.4,z);break;
    case 'shelf':case 'cabinet':
      box(.08,1,.8,-.46,.5);box(.08,1,.8,.46,.5);box(1,1,.08,0,.5,.38);
      for(let i=0;i<4;i++)box(.9,.05,.75,0,.08+i*.3);break;
    case 'sofa': box(1,.5,.85,0,.3);box(1,.65,.2,0,.65,.34);box(.13,.4,.85,-.44,.6);box(.13,.4,.85,.44,.6);break;
    case 'fridge': box(.88,1,.9,0,.5,0,'#dbe1d4');box(.06,.3,.04,.3,.5,-.47,'#4e5e57');break;
    case 'sink':case 'toilet': box(.85,.65,.7,0,.33,0,'#d9e1d5');cylinder(.36,.32,.18,0,.72,-.07,'#eef0df');break;
    case 'container': box(.88,.85,.88,0,.43);box(.98,.1,.98,0,.9);break;
    case 'animal': box(.5,.4,.8,0,.4);box(.35,.4,.32,0,.65,-.4);for(const x of [-.2,.2])for(const z of [-.28,.28])box(.1,.3,.1,x,.15,z);break;
    case 'zombie': cylinder(.2,.17,.55,0,.5,0);add(new T.IcosahedronGeometry(.16,0),0,.9,0);box(.13,.3,.14,-.11,.15);box(.13,.3,.14,.11,.15);break;
    case 'item': box(.5,.25,.5,0,.15);break;
    default: box(.85,.85,.85,0,.425);
  }
  const result=mergeGeometries(parts,false);
  parts.forEach(p=>p.dispose());
  return result;
}
