package net.lofers.scenario;
import java.nio.file.*;
import java.util.concurrent.*;
final class TerminalJournalFixture {
    static void check(boolean value){if(!value)throw new AssertionError("terminal_journal");}
    static TerminalJournal.Receipt receipt(int i){return new TerminalJournal.Receipt("fixture",new CivilianPool.Token("epoch",4096+i,1,"resident"+i,1),"corpse"+i,"digest");}
    static TerminalJournal.State await(TerminalJournal j,TerminalJournal.Receipt r)throws Exception{
        for(int i=0;i<200;i++){var s=j.submit(r);if(s!=TerminalJournal.State.PENDING)return s;Thread.sleep(5);}throw new AssertionError("writer_timeout");
    }
    static void run()throws Exception{
        check(EncounterCleanupGate.clearance(0,0,false,false));
        check(!EncounterCleanupGate.clearance(1,0,false,true));
        check(!EncounterCleanupGate.clearance(0,1,false,true));
        check(!EncounterCleanupGate.clearance(2,2,true,true));
        check(!EncounterCleanupGate.clearance(1,1,true,false));
        check(!EncounterCleanupGate.absent(1,1,false));
        check(EncounterCleanupGate.absent(1,1,true));
        check(EncounterCleanupGate.absent(0,0,false));
        Path dir=Path.of("artifacts/scenario-agent/terminal-fixture-"+java.util.UUID.randomUUID());
        try(var j=new TerminalJournal(dir)){
            check(await(j,receipt(0))==TerminalJournal.State.DURABLE);
            check(j.submit(receipt(0))==TerminalJournal.State.DURABLE);
            boolean conflict=false;try{j.submit(new TerminalJournal.Receipt("fixture",receipt(0).token(),"other","digest"));}catch(IllegalStateException e){conflict=true;}check(conflict);
            j.forget(receipt(0));check(await(j,receipt(0))==TerminalJournal.State.DURABLE);
        }
        check(TerminalJournal.unresolvedOnRestart(dir).size()==1);
        try(var restarted=new TerminalJournal(dir)){check(await(restarted,receipt(0))==TerminalJournal.State.UNRESOLVED);}

        try(var j=new TerminalJournal((k,p)->{throw new java.io.IOException("injected_disk_failure");})){
            check(await(j,receipt(1))==TerminalJournal.State.UNRESOLVED);check(j.submit(receipt(1))==TerminalJournal.State.UNRESOLVED);
        }
        var latch=new CountDownLatch(1);
        try(var j=new TerminalJournal((k,p)->latch.await())){
            for(int i=0;i<4;i++)check(j.submit(receipt(i))==TerminalJournal.State.PENDING);
            check(j.submit(receipt(4))==TerminalJournal.State.PENDING);latch.countDown();
            for(int i=0;i<4;i++){check(await(j,receipt(i))==TerminalJournal.State.DURABLE);j.forget(receipt(i));}
            check(await(j,receipt(4))==TerminalJournal.State.DURABLE);
        }finally{latch.countDown();}
        System.out.println("Terminal journal: durability, duplicates, conflict, failure, bounded queue and restart quarantine passed");
    }
}
