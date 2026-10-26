package dev.slipstream;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Finite reference-counted asynchronous loads. Used only from the world thread.
 * Completion is polled there; cancelled late results never gain retention. */
final class ScopedLoads<K,V> implements AutoCloseable {
 final class Entry {int users;CompletableFuture<V> loading;V value;Throwable error;}
 final class Lease implements AutoCloseable {
  final Set<K> keys;boolean closed;
  Lease(Set<K> keys){this.keys=Set.copyOf(keys);}
  boolean ready(){return !closed&&keys.stream().allMatch(k->{var e=entries.get(k);return e!=null&&e.value!=null;});}
  boolean failed(){return keys.stream().anyMatch(k->{var e=entries.get(k);return e!=null&&e.error!=null;});}
  @Override public void close(){if(closed)return;closed=true;for(K key:keys){var e=entries.get(key);if(e!=null&&--e.users==0){entries.remove(key);if(e.value!=null)release.accept(e.value);else if(e.loading!=null&&!e.loading.isDone())abandoned.add(e.loading);}}}
 }
 final List<CompletableFuture<V>> abandoned=new ArrayList<>();
 final Map<K,Entry> entries=new LinkedHashMap<>();final int budget;
 final Function<K,CompletableFuture<V>> load;final Consumer<V> retain,release;boolean closed;
 ScopedLoads(int budget,Function<K,CompletableFuture<V>> load,Consumer<V> retain,Consumer<V> release){this.budget=budget;this.load=load;this.retain=retain;this.release=release;}
 Lease request(Set<K> keys){if(closed)throw new IllegalStateException("Chunk loader closed");long extra=keys.stream().filter(k->!entries.containsKey(k)).count();if(entries.size()+extra>budget)return null;for(K key:keys)entries.computeIfAbsent(key,k->new Entry()).users++;return new Lease(keys);}
 void tick(){
  abandoned.removeIf(CompletableFuture::isDone);int inFlight=abandoned.size();
  for(var e:entries.values())if(e.loading!=null&&e.value==null&&e.error==null){
   if(!e.loading.isDone()){inFlight++;continue;}
   try{V value=Objects.requireNonNull(e.loading.join());retain.accept(value);e.value=value;}catch(RuntimeException error){e.error=error;}
  }
  int started=0;
  for(var row:entries.entrySet()){var e=row.getValue();if(e.loading==null&&e.error==null&&inFlight<8&&started<2){try{e.loading=load.apply(row.getKey());inFlight++;started++;}catch(RuntimeException error){e.error=error;}}}
 }
 @Override public void close(){if(closed)return;closed=true;for(var e:entries.values())if(e.value!=null)release.accept(e.value);entries.clear();abandoned.clear();}
}
