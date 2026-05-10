import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { CognitoAuthService } from '../cognito-auth.service';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [FormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatCardModule],
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignupComponent {
  private cognitoAuth = inject(CognitoAuthService);

  email = '';
  password = '';
  loading = signal(false);
  error = signal('');
  success = signal(false);

  async signup(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      await this.cognitoAuth.signUp(this.email, this.password);
      this.success.set(true);
    } catch (err: any) {
      this.error.set(err.message ?? 'Signup failed');
    } finally {
      this.loading.set(false);
    }
  }
}
