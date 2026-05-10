import { Component, ChangeDetectionStrategy, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TopBarComponent } from './top-bar';
import { WebSocketService } from '../../core/services/websocket.service';
import { TierService } from '../../core/services/tier.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, TopBarComponent],
  templateUrl: './app-layout.html',
  styleUrl: './app-layout.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppLayoutComponent implements OnInit {
  private ws = inject(WebSocketService);
  private tierService = inject(TierService);

  ngOnInit() {
    this.ws.connect();
    this.tierService.load();
  }
}
